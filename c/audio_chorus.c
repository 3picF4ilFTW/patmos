#include <machine/spm.h>
#include <machine/rtc.h>
#include <stdio.h>
#include <math.h>

#define ONE_16b 0x7FFF
#define BUFFER_SIZE 128
#define Fs 52083 // Hz

#define COMB_FILTER_ORDER_1PLUS 3 // FOR CHORUS comb filter


/* Chorus:
     -Implemented as a 2nd order FIR comb filter
     -Modulation of cascaded signals is sinusoidal
*/

#define AUDIO_BUFFER_LENGTH 2083 // for a delay of up to 2083*10e3 / 52083 =

#include "audio.h"
#include "audio.c"


// LOCATION IN LOCAL SCRATCHPAD MEMORY
#define ACCUM_ADDR  0x00000000
#define Y_ADDR      ( ACCUM_ADDR  + 2 * sizeof(int) )
#define G_ADDR      ( Y_ADDR      + 2 * sizeof(short) )

#if ( (COMB_FILTER_ORDER_1PLUS % 2) == 0 ) //if it's even
#define DEL_ADDR    ( G_ADDR      + COMB_FILTER_ORDER_1PLUS * sizeof(short) )
#else // if it's odd
#define DEL_ADDR    ( G_ADDR      + COMB_FILTER_ORDER_1PLUS * sizeof(short) + 2 ) //to align with 4-byte word
#endif

#define PNT_ADDR    ( DEL_ADDR    + COMB_FILTER_ORDER_1PLUS * sizeof(int) )
#define C1_PNT_ADDR ( PNT_ADDR    + sizeof(int) )
#define C2_PNT_ADDR ( C1_PNT_ADDR + sizeof(int) )

//SPM Variables
volatile _SPM int *accum             = (volatile _SPM int *)        ACCUM_ADDR;
volatile _SPM short *y               = (volatile _SPM short *)      Y_ADDR; // y[2]: output
volatile _SPM short *g               = (volatile _SPM short *)      G_ADDR; // g[FILTER_ORDER_1PLUS]: array of gains [... g2, g1, g0]
volatile _SPM int *del               = (volatile _SPM int *)        DEL_ADDR; // del[FILTER_ORDER_1PLUS]: array of delays [...d2, d1, 0]
volatile _SPM int *pnt               = (volatile _SPM int *)        PNT_ADDR; //pointer indicates last position of audio_buffer
volatile _SPM int *c1_pnt            = (volatile _SPM int *)        C1_PNT_ADDR; //sin pointer 1
volatile _SPM int *c2_pnt            = (volatile _SPM int *)        C2_PNT_ADDR; //sin pointer 2

//location in external SRAM
//volatile _SPM short (*audio_buffer)[2] = (volatile _SPM short (*)[2]) AUDIO_BUFFER_ADDR; // audio_buffer[AUDIO_BUFFER_LENGTH][2]
volatile short audio_buffer[AUDIO_BUFFER_LENGTH][2];
//sin array storage:
const int SIN1_PERIOD = 52083; // 1 second
const int SIN2_PERIOD = 40000; // ~0.8 seconds
int sinArray[Fs];
int usedArray1[SIN1_PERIOD];
int usedArray2[SIN2_PERIOD];

int main() {

    setup(1); //guitar enabled

    //*shiftLeft = 0;

    //gains
    g[2] = ONE_16b * 0.45; //g0
    g[1] = ONE_16b * 0.4; //g1
    g[0] = ONE_16b * 0.4; //g2

    //delays
    del[2] = 0; //always d0 = 0

    //store sin: 1 second betwen -1 and 1
    storeSin(sinArray, Fs, 0, ONE_16b);

    //calculate interpolated array:
    float arrayDivider = (float)Fs/(float)SIN1_PERIOD;
    printf("Array Divider is: %f\n", arrayDivider);
    float mult1 = AUDIO_BUFFER_LENGTH*0.6;
    float mult2 = AUDIO_BUFFER_LENGTH*0.03;
    printf("Downsampling sin...\n");
    for(int i=0; i<SIN1_PERIOD; i++) {
        //offset = AUDIO_BUFFER_LENGTH*0.6, amplitude = AUDIO_BUFFER_LENGTH * 0.02
        usedArray1[i] = mult1 + (mult2/ONE_16b)*sinArray[(int)floor(i*arrayDivider)];
    }
    printf("Done 1st...\n");
    arrayDivider = (float)Fs/(float)SIN2_PERIOD;
    printf("Array Divider is: %f\n", arrayDivider);
    mult1 = AUDIO_BUFFER_LENGTH*0.4;
    mult2 = AUDIO_BUFFER_LENGTH*0.016;
    printf("Downsampling sin...\n");
    for(int i=0; i<SIN2_PERIOD; i++) {
        //offset = AUDIO_BUFFER_LENGTH*0.4, amplitude = AUDIO_BUFFER_LENGTH * 0.012
        usedArray2[i] = mult1 + (mult2/ONE_16b)*sinArray[(int)floor(i*arrayDivider)];
    }
    printf("Done 2nd!\n");

    //storeSin(usedArray1, SIN1_PERIOD, ( AUDIO_BUFFER_LENGTH*0.6 ), ( AUDIO_BUFFER_LENGTH * 0.02) );
    //storeSin(usedArray2, SIN2_PERIOD, ( AUDIO_BUFFER_LENGTH*0.4 ), ( AUDIO_BUFFER_LENGTH * 0.012) );
    printf("sins storage done!\n");

    //CPU cycles stuff
    //int CPUcycles[1000] = {0};
    //int cpu_pnt = 0;

    // enable input and output
    *audioDacEnReg = 1;
    *audioAdcEnReg = 1;

    setInputBufferSize(BUFFER_SIZE);
    setOutputBufferSize(BUFFER_SIZE);

    *pnt = AUDIO_BUFFER_LENGTH - 1; //start on top
    *c1_pnt = 0;
    *c2_pnt = 0;
    while(*keyReg != 3) {
        // SINUSOIDAL MODULATION OF DELAY LENGTH
        del[0] = usedArray1[*c1_pnt];
        del[1] = usedArray2[*c2_pnt];
        *c1_pnt = (*c1_pnt + 1) % SIN1_PERIOD;
        *c2_pnt = (*c2_pnt + 1) % SIN2_PERIOD;
        //audio, read sample
        getInputBuffer(&audio_buffer[*pnt][0], &audio_buffer[*pnt][1]);
        //calculate AUDIO comb filter
        combFilter_2nd(AUDIO_BUFFER_LENGTH, pnt, audio_buffer, y, accum, g, del);
        setOutputBuffer(y[0], y[1]);
        //update pointer
        if(*pnt == 0) {
            *pnt = AUDIO_BUFFER_LENGTH - 1;
        }
        else {
            *pnt = *pnt - 1;
        }
        /*
        //store CPU Cycles
        CPUcycles[cpu_pnt] = get_cpu_cycles();
        cpu_pnt++;
        if(cpu_pnt == 1000) {
            break;
        }
        */
    }
    /*
    for(int i=1; i<1000; i++) {
        printf("%d\n", (CPUcycles[i]-CPUcycles[i-1]));
    }
    */
    return 0;
}
