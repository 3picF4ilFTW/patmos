/*
#include <machine/spm.h>
#include <machine/rtc.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
*/

#include "libaudio/audio.h"
#include "libaudio/audio.c"

const int NOC_MASTER = 0;

#define MP_CHAN_SHORTS_AMOUNT 2

#define MP_CHAN_1_ID 1
#define MP_CHAN_1_NUM_BUF 1
#define MP_CHAN_1_MSG_SIZE MP_CHAN_SHORTS_AMOUNT * 2 // 2 shorts = 4 bytes (actually always multiples of 4)

#define MP_CHAN_2_ID 2
#define MP_CHAN_2_NUM_BUF 1
#define MP_CHAN_2_MSG_SIZE MP_CHAN_SHORTS_AMOUNT * 2 // 2 shorts = 4 bytes


void thread1(void* args) {
    volatile _UNCACHED int **inArgs = (volatile _UNCACHED int **) args;
    volatile _UNCACHED int *exitP      = inArgs[0];

    //MP: Create the queuing ports
    qpd_t * chan1 = mp_create_qport(MP_CHAN_1_ID, SINK,
        MP_CHAN_1_MSG_SIZE, MP_CHAN_1_NUM_BUF);
    qpd_t * chan2 = mp_create_qport(MP_CHAN_2_ID, SOURCE,
        MP_CHAN_2_MSG_SIZE, MP_CHAN_2_NUM_BUF);
    // Initialize the communication channels
    int nocret = mp_init_ports();

    /*
      AUDIO STUFF HERE
    */

    // exit with return value
    int ret = 0;
    corethread_exit(&ret);
    return;
}



int main() {

    //create corethread type var
    corethread_t threadOne = (corethread_t) 1;
    //arguments to thread 1 function
    int exit = 0;
    volatile _UNCACHED int *exitP = (volatile _UNCACHED int *) &exit;
    volatile _UNCACHED int (*thread1_args[1]);
    thread1_args[0] = exitP;

    printf("starting thread and NoC channels...\n");
    //set thread function and start thread
    corethread_create(&threadOne, &thread1, (void*) thread1_args);

    // MP: create message passing ports
    qpd_t * chan1 = mp_create_qport(MP_CHAN_1_ID, SOURCE,
        MP_CHAN_1_MSG_SIZE, MP_CHAN_1_NUM_BUF);
    qpd_t * chan2 = mp_create_qport(MP_CHAN_2_ID, SINK,
        MP_CHAN_2_MSG_SIZE, MP_CHAN_2_NUM_BUF);
    // Initialize the communication channels
    int nocret = mp_init_ports();
    if(nocret == 1) {
        printf("Thread and NoC initialised correctly\n");
    }
    else {
        printf("ERROR: Problem with NoC initialisation\n");
    }

    /*
      AUDIO STUFF HERE
    */

    #if GUITAR == 1
    setup(1); //for guitar
    #else
    setup(0); //for volca
    #endif

    // enable input and output
    *audioDacEnReg = 1;
    *audioAdcEnReg = 1;

    setInputBufferSize(BUFFER_SIZE);
    setOutputBufferSize(BUFFER_SIZE);

    struct AudioFX audio1;
    struct AudioFX *audio1P = &audio1;
    alloc_dry_vars(audio1P, -1, -1);

    struct AudioFX audio2;
    struct AudioFX *audio2P = &audio2;
    alloc_dry_vars(audio2P, -1, -1);


    //CPU cycles stuff
    //int CPUcycles[1000] = {0};
    //int cpu_pnt = 0;

    while(*keyReg != 3) {
        audioIn(audio1P);
        audio_dry(audio1P);
        audioChainCore(audio1P, audio2P);
        audio_dry(audio2P);
        audioOut(audio2P);

        /*
        //store CPU Cycles
        CPUcycles[cpu_pnt] = get_cpu_cycles();
        cpu_pnt++;
        if(cpu_pnt == 1000) {
            break;
        }
        */
    }

    //exit stuff
    printf("exit here!\n");
    exit = 1;
    printf("waiting for thread 1 to finish...\n");
    //join with thread 1
    int *retval;
    corethread_join(threadOne, (void **)&retval);
    printf("thread 1 finished!\n");
    /*
    for(int i=1; i<1000; i++) {
        printf("%d\n", (CPUcycles[i]-CPUcycles[i-1]));
    }
    */

    return 0;
}
