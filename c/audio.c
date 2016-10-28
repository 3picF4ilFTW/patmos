#include <machine/spm.h>
#include <machine/rtc.h>
#include <stdio.h>
#include <stdlib.h>
//#include <math.h>
#include "audio.h"

#ifndef ONE_16b
#define ONE_16b 0x7FFF
#endif
#ifndef Fs
#define Fs 52083 // Hz
#endif

/*
 * @file		Audio_setup.c
 * @author	Daniel Sanz Ausin s142290 & Fabian Goerge s150957
 * @brief	Setting up all the registers in the audio interface
 */

/*
 * @brief		Writes the supplied data to the address register,
 sets the request signal and waits for the acknowledge signal.
 * @param[in]	addr	the address of which register to write to.
 Has to be 7 bit long.
 * @param[in]	data	the data thats supposed to be written.
 Has to be 9 Bits long
 * @reutrn		returns 0 if successful and a negative number if there was an error.
 */
int writeToI2C(char* addrC,char* dataC) {
  int addr = 0;
  int data = 0;

  //Convert binary String of address to int
  for(int i = 0; i < 7; i++) {
    addr *= 2;
    if (*addrC++ == '1') addr += 1;
  }

  //Convert binary String of data to int
  for(int i = 0; i < 9; i++) {
    data *= 2;
    if (*dataC++ == '1') data += 1;
  }

  //Debug info:
  printf("Sending Data: %i to address %i\n",data,addr);

  *i2cDataReg = data;
  *i2cAdrReg	= addr;
  *i2cReqReg	= 1;


  while(*i2cAckReg == 0) {
    printf("Waiting ...\n");
    //Maybe input something like a timeout ...
  }
  for (int i = 0; i<200; i++)  { *i2cReqReg=0; }

  printf("success\n");

  return 0;



}

/*
 * @brief	Sets the default values
 */

void setup(int guitar) {

  /*
  //----------Line in---------------------
  char *addrLeftIn  = "0000000";
  char *dataLineIn  = "100010111";	//disable Mute, Enable Simultaneous Load, LinVol: 10111 - Set volume to 23 (of 31)
  writeToI2C(addrLeftIn,dataLineIn);

  char *addrRigthIn = "0000001";
  writeToI2C(addrRigthIn,dataLineIn);
  */

  //----------Headphones---------------------
  char *addrLeftHead  = "0000010";
  char *dataHeadphone = "001111001";	// disable simultaneous loads, zero cross disable, LinVol: 1111001 (0db)
  writeToI2C(addrLeftHead,dataHeadphone);

  char *addrRightHead = "0000011";
  writeToI2C(addrRightHead,dataHeadphone);

  //--------Analogue Audio Path Control-----
  char *addrAnalogue  = "0000100";
  if(guitar == 0) {
      char *dataAnalogue = "000010010"; //DAC selected, rest disabled, MIC muted, Line input select to ADC
      writeToI2C(addrAnalogue,dataAnalogue);
  }
  else {
      char *dataAnalogueGuit = "000010101"; //MIC selected to ADC, MIC enabled, MIC boost enabled
      writeToI2C(addrAnalogue,dataAnalogueGuit);
  }


  //--------Digital Audio Path Control-----
  char *addrDigital  = "0000101";
  char *dataDigital  = "000000001";	//disable soft mute and disable de-emphasis, disable highpass filter
  writeToI2C(addrDigital,dataDigital);


  //---------Digital Audio Interface Format---------
  char *addrInterface  = "0000111";
  char *dataInterface = "000010011"; //BCLK not inverted, slave, right-channel-right-data, LRP = A mode for DSP, 16-bit audio, DSP mode
  writeToI2C(addrInterface,dataInterface);


  //--------Sampling Control----------------
  char *addrSample  = "0001000";
  char *dataSample  = "000000000"; //USB mode, BOSR=1, Sample Rate = 44.1 kHz both for ADC and DAC
  writeToI2C(addrSample,dataSample);

  printf("FINISHED SETUP!\n");
}


/*
 * @brief		changes the volume of the audio output.
 * @param[in]	vol 	in db. Has to be between +6 and -73
 * @reutrn		returns 0 if successful and a negative number if there was an error.
 */
int changeVolume(int vol) {
  //127--1111111 = +6dB
  //121--1111001 = 0 dB
  //48--0110000 = -73dB
  if ((vol<-73) || (vol>6)) {
    return -1;	//Invalid input.
  }

  //Since both Left Channel Zero Cross detect and simultaneous Load are disabled this can be send imideatly:
  //LEFT:
  *i2cDataReg = vol + 121;
  *i2cAdrReg	 = 2;
  *i2cReqReg	 = 1;
  while(*i2cAckReg == 0);
  for (int i = 0; i<200; i++)  { *i2cReqReg=0; }

  //RIGHT:
  *i2cDataReg = vol + 121;
  *i2cAdrReg	 = 3;
  *i2cReqReg	 = 1;
  while(*i2cAckReg == 0);
  for (int i = 0; i<200; i++)  { *i2cReqReg=0; }

  return 0;
}


int isPowerOfTwo (unsigned int x) {
 while (((x % 2) == 0) && x > 1) /* While x is even and > 1 */
   x /= 2;
 return (x == 1);
}

/*
 * @brief	sets the size of the input (ADC) buffer. Must be a power of 2
 * @param[in]	bufferSize	length of the buffer
 * @return	returns 0 if successful and a 1 if there was an error.
 */
int setInputBufferSize(int bufferSize) {
  if(isPowerOfTwo(bufferSize)) {
    printf("Input buffer size set to %d\n", bufferSize);
    *audioAdcBufferSizeReg = bufferSize;
    return 0;
  }
  else {
    printf("ERROR: Buffer Size must be power of 2\n");
    return 1;
  }
}

/*
 * @brief	 sets the size of the output (DAC) buffer. Must be a power of 2
 * @param[in]	 bufferSize	length of the buffer
 * @return	 returns 0 if successful and a 1 if there was an error.
 */
int setOutputBufferSize(int bufferSize) {
  if(isPowerOfTwo(bufferSize)) {
    printf("Output buffer size set to %d\n", bufferSize);
    *audioDacBufferSizeReg = bufferSize;
    return 0;
  }
  else {
    printf("ERROR: Buffer Size must be power of 2\n");
    return 1;
  }
}

/*
 * @brief	reads data from the input (ADC) buffer into Patmos
 * @param[in]	*l	pointer to left audio data
 * @param[in]	*r	pointer to right audio data
 * @return	returns 0 if successful and a 1 if there was an error.
 */
int getInputBufferSPM(volatile _SPM short *l, volatile _SPM short *r) {
  while(*audioAdcBufferEmptyReg == 1);// wait until not empty
  *audioAdcBufferReadPulseReg = 1; // begin pulse
  *audioAdcBufferReadPulseReg = 0; // end pulse
  *l = *audioAdcLReg;
  *r = *audioAdcRReg;
  return 0;
}

int getInputBuffer(volatile short *l, volatile short *r) {
  while(*audioAdcBufferEmptyReg == 1);// wait until not empty
  *audioAdcBufferReadPulseReg = 1; // begin pulse
  *audioAdcBufferReadPulseReg = 0; // end pulse
  *l = *audioAdcLReg;
  *r = *audioAdcRReg;
  return 0;
}

/*
 * @brief	writes data from patmos into the output (DAC) buffer
 * @param[in]	l	left audio data
 * @param[in]	r	right audio data
 * @return	returns 0 if successful and a 1 if there was an error.
 */
int setOutputBuffer(short l, short r) {
  //write data first: it will stay in AudioInterface, won't go to
  //AudioDacBuffer until the write pulse
  *audioDacLReg = l;
  *audioDacRReg = r;
  while(*audioDacBufferFullReg == 1); // wait until not full
  *audioDacBufferWritePulseReg = 1; // begin pulse
  *audioDacBufferWritePulseReg = 0; // end pulse

  return 0;
}


//__attribute__((always_inline))
int filterIIR_1st(int pnt_i, volatile _SPM short (*x)[2], volatile _SPM short (*y)[2], volatile _SPM int *accum, volatile _SPM short *B, volatile _SPM short *A, int shiftLeft) {
    int pnt; //pointer for x_filter
    accum[0] = 0;
    accum[1] = 0;
    for(int i=0; i<2; i++) { //FILTER_ORDER_1PLUS = 2
        pnt = (pnt_i + i + 1) % 2; //FILTER_ORDER_1PLUS = 2
        // SIGNED SHIFT (arithmetical): losing a 6-bit resolution
        accum[0] += (B[i]*x[pnt][0]) >> 6;
        accum[0] -= (A[i]*y[pnt][0]) >> 6;
        accum[1] += (B[i]*x[pnt][1]) >> 6;
        accum[1] -= (A[i]*y[pnt][1]) >> 6;
    }
    //accumulator limits: [ (2^(30-6-1))-1 , -(2^(30-6-1)) ]
    //accumulator limits: [ 0x7FFFFF, 0x800000 ]
    // digital saturation
    for(int i=0; i<2; i++) {
        if (accum[i] > 0x7FFFFF) {
            accum[i] = 0x7FFFFF;
        }
        else {
            if (accum[i] < -0x800000) {
                accum[i] = -0x800000;
            }
        }
    }
    y[pnt_i][0] = accum[0] >> (9-shiftLeft);
    y[pnt_i][1] = accum[1] >> (9-shiftLeft);

    return 0;
}

//__attribute__((always_inline))
int filterIIR_2nd(int pnt_i, volatile _SPM short (*x)[2], volatile _SPM short (*y)[2], volatile _SPM int *accum, volatile _SPM short *B, volatile _SPM short *A, int shiftLeft) {
    int pnt; //pointer for x_filter
    accum[0] = 0;
    accum[1] = 0;
    for(int i=0; i<3; i++) { //FILTER_ORDER_1PLUS = 3
        pnt = (pnt_i + i + 1) % 3; //FILTER_ORDER_1PLUS = 3
        // SIGNED SHIFT (arithmetical): losing a 6-bit resolution
        accum[0] += (B[i]*x[pnt][0]) >> 6;
        accum[0] -= (A[i]*y[pnt][0]) >> 6;
        accum[1] += (B[i]*x[pnt][1]) >> 6;
        accum[1] -= (A[i]*y[pnt][1]) >> 6;
    }
    //accumulator limits: [ (2^(30-6-1))-1 , -(2^(30-6-1)) ]
    //accumulator limits: [ 0x7FFFFF, 0x800000 ]
    // digital saturation
    for(int i=0; i<2; i++) {
        if (accum[i] > 0x7FFFFF) {
            accum[i] = 0x7FFFFF;
        }
        else {
            if (accum[i] < -0x800000) {
                accum[i] = -0x800000;
            }
        }
    }
    y[pnt_i][0] = accum[0] >> (9-shiftLeft);
    y[pnt_i][1] = accum[1] >> (9-shiftLeft);

    return 0;
}

int storeSinInterpol(int *sinArray, int SIZE, int OFFSET, int AMP, short *fracArray, float *zeiger) {
    printf("Storing sin array and frac array...\n");
    //float zeiger;
    for(int i=0; i<SIZE; i++) {
        zeiger[i] = (float)OFFSET + ((float)AMP)*sin(2.0*M_PI* i / SIZE);
        sinArray[i] = (int)floor(zeiger[i]);
        fracArray[i] = (zeiger[i]-(float)sinArray[i])*(pow(2,15)-1);
        //printf("zeiger=%f, sinArray[%d]=%d, fracArray[%d]=%d\n", zeiger, i, sinArray[i], i, fracArray[i]);
    }
    printf("Sin array and frac array storage done\n");

    return 0;
}

int storeSin(int *sinArray, int SIZE, int OFFSET, int AMP) {
    printf("Storing sin array...\n");
    for(int i=0; i<SIZE; i++) {
        sinArray[i] = OFFSET + AMP*sin(2.0*M_PI* i / SIZE);
    }
    /*
    //FIX THIS:
    const int HALF  = (int)ceil(SIZE/2);
    const int ADDER = SIZE%2;
    //first half:
    for(int i=0; i<HALF; i++) {
        sinArray[i] = OFFSET + AMP*sin(2.0*M_PI* i / SIZE);
    }
    //2nd half: odd simmetry
    for(int i=0; i<HALF+ADDER; i++) {
        sinArray[HALF + i] = -sinArray[HALF - (i-ADDER)];
    }
    */
    printf("Sin array storage done\n");

    return 0;
}

int checkRanges(int FILT_ORD_1PL, float *Bfl, float *Afl, volatile _SPM int *shiftLeft, int fixedShift) {
    if(!fixedShift) {
        *shiftLeft = 0;
    }
    //check for overflow if coefficients
    float maxVal = 0;
    for(int i=0; i<FILT_ORD_1PL; i++) {
        if( (fabs(Bfl[i]) > 1) && (fabs(Bfl[i]) > maxVal) ) {
            maxVal = fabs(Bfl[i]);
        }
        if( (fabs(Afl[i]) > 1) && (fabs(Afl[i]) > maxVal) ) {
            maxVal = fabs(Afl[i]);
        }
    }
    //if coefficients were too high, scale down
    if(maxVal > 1) {
        if (fixedShift == 1) { // if shiftLeft was fixed
            if (*shiftLeft > 0) {
                maxVal = maxVal / (*shiftLeft * 2) ; //similar to shifting
            }
            if (maxVal > 1) { // if its still out of range
                printf("ERROR! coefficients are out of range, max is %f\n", maxVal);
                return 1;
            }
        }
        if(!fixedShift) {
            printf("Greatest coefficient found is %f, ", maxVal);
        }
    }
    while(maxVal > 1) { //loop until maxVal < 1
        *shiftLeft = *shiftLeft + 1; //here we shift right, but the IIR result will be shifted left
        maxVal--;
    }
    if(!fixedShift) {
        printf("shift left amount is %d\n", *shiftLeft);
    }

    return 0;
}


int filter_coeff_bp_br(int FILT_ORD_1PL, volatile _SPM short *B, volatile _SPM short *A, int Fc, int Fb, volatile _SPM int *shiftLeft, int fixedShift) {
    // if FILTER ORDER = 1, Fb is ignored
    float c, d;
    float Bfl[FILT_ORD_1PL];
    float Afl[FILT_ORD_1PL];
    //init to 0
    for(int i=0; i< FILT_ORD_1PL; i++) {
        Bfl[i] = 0;
        Afl[i] = 0;
    }
    if(FILT_ORD_1PL == 2) { //1st order
        if(!fixedShift) {
            printf("Calculating 1st order coefficients...\n");
        }
        c = ( tan(M_PI * Fc / Fs) - 1) / ( tan(M_PI * Fc / Fs) + 1 );
        Bfl[1] = c; // b0
        Bfl[0] = 1; // b1
        Afl[0] = c; // a1
    }
    else {
        if(FILT_ORD_1PL == 3) { // 2nd order
            if(!fixedShift) {
                printf("Calculating 2nd order coefficients...\n");
            }
            c = ( tan(M_PI * Fb / Fs) - 1) / ( tan(M_PI * Fb / Fs) + 1 );
            d = -1 * cos(2 * M_PI * Fc / Fs);
            Bfl[2] = -1 * c; // b0
            Bfl[1] = d * (1 - c); // b1
            Bfl[0] = 1; // b2
            Afl[1] = d * (1 - c); // a1
            Afl[0] = -1 * c; // a2
        }
    }
    // check ranges and set leftShift amount
    int notRangesOK = checkRanges(FILT_ORD_1PL, Bfl, Afl, shiftLeft, fixedShift);
    if (notRangesOK == 1) {
        return 1;
    }
    // now all coefficients should be between 0 and 1
    for(int i=0; i<FILT_ORD_1PL; i++) {
        B[i] = (short) ( (int) (ONE_16b * Bfl[i]) >> *shiftLeft );
        A[i] = (short) ( (int) (ONE_16b * Afl[i]) >> *shiftLeft );
    }
    if(!fixedShift) {
        if(FILT_ORD_1PL == 2) {
            printf("done! c: %f, b0: %d, b1: %d, a0, %d, a1: %d\n", c, B[1], B[0], A[1], A[0]);
        }
        if(FILT_ORD_1PL == 3) {
            printf("done! c: %f, d: %f, b0: %d, b1: %d, b2 : %d, a0: %d, a1: %d, a2: %d\n", c, d, B[2], B[1], B[0], A[2], A[1], A[0]);
        }
    }

    return 0;
}


int filter_coeff_hp_lp(int FILT_ORD_1PL, volatile _SPM short *B, volatile _SPM short *A, int Fc, float Q, volatile _SPM int *shiftLeft, int fixedShift, int type) {
    float K = tan(M_PI * Fc / Fs);// K is same for all
    float Bfl[FILT_ORD_1PL];
    float Afl[FILT_ORD_1PL];
    //init to 0
    for(int i=0; i< FILT_ORD_1PL; i++) {
        Bfl[i] = 0;
        Afl[i] = 0;
    }
    float common_factor;
    if(type == 0) { //LPF
        if(FILT_ORD_1PL == 2) { //1st order
            printf("Calculating LPF for 1st order...\n");
            Bfl[1] = K/(K+1); //b0
            Bfl[0] = K/(K+1); //b1
            Afl[0] = (K-1)/(K+1); //a1
        }
        else {
            if(FILT_ORD_1PL == 3) { //2nd order
                printf("Calculating LPF for 2nd order...\n");
                common_factor = 1/(pow(K,2)*Q + K + Q);
                Bfl[2] = pow(K,2)*Q*common_factor; //b0
                Bfl[1] = 2*pow(K,2)*Q*common_factor; //b1
                Bfl[0] = pow(K,2)*Q*common_factor; //b2
                Afl[1] = 2*Q*(pow(K,2)-1)*common_factor; //a1
                Afl[0] = (pow(K,2)*Q - K + Q)*common_factor; //a2
            }
        }
    }
    else {
        if(type == 1) { // HPF
            if(FILT_ORD_1PL == 2) { //1st order
                printf("Calculating HPF for 1st order...\n");
                Bfl[1] =  1/(K+1); //b0
                Bfl[0] = -1/(K+1); //b1
                Afl[0] = (K-1)/(K+1); //a1
            }
            else {
                if(FILT_ORD_1PL == 3) { //2nd order
                    printf("Calculating HPF for 2nd order...\n");
                    common_factor = 1/(pow(K,2)*Q + K + Q);
                    Bfl[2] = Q*common_factor; //b0
                    Bfl[1] = -2*Q*common_factor; //b1
                    Bfl[0] = Q*common_factor; //b2
                    Afl[1] = 2*Q*(pow(K,2)-1)*common_factor; //a1
                    Afl[0] = (pow(K,2)*Q - K + Q)*common_factor; //a2
                }
            }
        }
    }
    // check ranges and set leftShift amount
    int notRangesOK = checkRanges(FILT_ORD_1PL, Bfl, Afl, shiftLeft, fixedShift);
    if (notRangesOK == 1) {
        return 1;
    }
    // now all coefficients should be between 0 and 1
    for(int i=0; i<FILT_ORD_1PL; i++) {
        B[i] = (short) ( (int) (ONE_16b * Bfl[i]) >> *shiftLeft );
        A[i] = (short) ( (int) (ONE_16b * Afl[i]) >> *shiftLeft );
    }
    if(FILT_ORD_1PL == 2) {
        printf("done! K: %f, b0: %d, b1: %d, a0, %d, a1: %d\n", K, B[1], B[0], A[1], A[0]);
    }
    if(FILT_ORD_1PL == 3) {
        printf("done! K: %f, common_factor: %f, b0: %d, b1: %d, b2 : %d, a0: %d, a1: %d, a2: %d\n", K, common_factor, B[2], B[1], B[0], A[2], A[1], A[0]);
    }

    return 0;
}

__attribute__((always_inline))
int allpass_comb(int AP_BUFF_LEN, volatile _SPM int *pnt, volatile short (*ap_buffer)[2], volatile _SPM short *x, volatile _SPM short *y, volatile _SPM short *g, int printa) {
    int accum[2];
    int ap_pnt = (*pnt + AP_BUFF_LEN - 1) % AP_BUFF_LEN;
    for(int i=0; i<2; i++) {
        y[i] = ap_buffer[ap_pnt][i] - ( (x[i]*(*g)) >> 15 );
        accum[i] = ( (y[i]*(*g)) >> 15 ) + x[i];
        if(printa == 1) {
            printf("y[%d]=%d, ap_buffer[%d][%d]=%d, accum[%d]=%d\n", i, y[i], ap_pnt, i, ap_buffer[ap_pnt][i], i, accum[i]);
        }
        //check for overflow
        if(accum[i] > ONE_16b) {
            accum[i] = ONE_16b;
        }
        else {
            if(accum[i] < -ONE_16b) {
                accum[i] = -ONE_16b;
            }
        }
        ap_buffer[*pnt][i] = accum[i];
    }

    return 0;
}

__attribute__((always_inline))
int combFilter_1st(int AUDIO_BUFF_LEN, volatile _SPM int *pnt, volatile short (*audio_buffer)[2], volatile _SPM short *y, volatile _SPM int *accum, volatile _SPM short *g, volatile _SPM int *del) {
    accum[0] = 0;
    accum[1] = 0;
    int audio_pnt = (*pnt+del[0])%AUDIO_BUFF_LEN;
    accum[0] += (g[0]*audio_buffer[audio_pnt][0]) >> 6;
    accum[1] += (g[0]*audio_buffer[audio_pnt][1]) >> 6;
    audio_pnt = (*pnt+del[1])%AUDIO_BUFF_LEN;
    accum[0] += (g[1]*audio_buffer[audio_pnt][0]) >> 6;
    accum[1] += (g[1]*audio_buffer[audio_pnt][1]) >> 6;
    //accumulator limits: [ (2^(30-6-1))-1 , -(2^(30-6-1)) ]
    //accumulator limits: [ 0x7FFFFF, 0x800000 ]
    // digital saturation
    for(int i=0; i<2; i++) {
        if (accum[i] > 0x7FFFFF) {
            accum[i] = 0x7FFFFF;
        }
        else {
            if (accum[i] < -0x800000) {
                accum[i] = -0x800000;
            }
        }
    }
    y[0] = accum[0] >> 9;
    y[1] = accum[1] >> 9;

    return 0;
}

__attribute__((always_inline))
int combFilter_2nd(int AUDIO_BUFF_LEN, volatile _SPM int *pnt, volatile short (*audio_buffer)[2], volatile _SPM short *y, volatile _SPM int *accum, volatile _SPM short *g, volatile _SPM int *del) {
    accum[0] = 0;
    accum[1] = 0;
    int audio_pnt = (*pnt+del[0])%AUDIO_BUFF_LEN;
    accum[0] += (g[0]*audio_buffer[audio_pnt][0]) >> 6;
    accum[1] += (g[0]*audio_buffer[audio_pnt][1]) >> 6;
    audio_pnt = (*pnt+del[1])%AUDIO_BUFF_LEN;
    accum[0] += (g[1]*audio_buffer[audio_pnt][0]) >> 6;
    accum[1] += (g[1]*audio_buffer[audio_pnt][1]) >> 6;
    audio_pnt = (*pnt+del[2])%AUDIO_BUFF_LEN;
    accum[0] += (g[2]*audio_buffer[audio_pnt][0]) >> 6;
    accum[1] += (g[2]*audio_buffer[audio_pnt][1]) >> 6;
    //accumulator limits: [ (2^(30-6-1))-1 , -(2^(30-6-1)) ]
    //accumulator limits: [ 0x7FFFFF, 0x800000 ]
    // digital saturation
    for(int i=0; i<2; i++) {
        if (accum[i] > 0x7FFFFF) {
            accum[i] = 0x7FFFFF;
        }
        else {
            if (accum[i] < -0x800000) {
                accum[i] = -0x800000;
            }
        }
    }
    y[0] = accum[0] >> 9;
    y[1] = accum[1] >> 9;

    return 0;
}

//from 1/2! to 1/8!, represented as Q.15
const short MCLAURIN_FACTOR[7] = { 0x4000, 0x1555, 0x555, 0x111, 0x2d, 0x6, 0x1};

__attribute__((always_inline))
int distortion(volatile _SPM short *x, volatile _SPM short *y, volatile _SPM int *accum) {
    int neg;
    if(x[0] > 0) {
        for(int j=0; j<2; j++) {
            x[j] = -x[j];
        }
        neg = 0;
    }
    else {
        neg = 1;
    }
    // calculate maclaurin series
    short x_pow[2];
    for(int j=0; j<2; j++) {
        //before: initialise to first value:
        x_pow[j] = x[j];
        accum[j] = -x[j];
        //after that, rest of values:
        for(int i=0; i<5; i++) { // 5 for MacLaurin order 6
            //increase power, divide by factor
            x_pow[j] = (x_pow[j] * x[j]) >> 15;
            accum[j] -= (x_pow[j] * MCLAURIN_FACTOR[i]) >> 15;
        }
        //set output
        if (neg == 1) { //negative sign
            y[j] = -accum[j];
        }
        else { //positive sign
            y[j] = accum[j];
        }
    }

    return 0;
}

__attribute__((always_inline))
int fuzz(volatile _SPM short *x, volatile _SPM short *y, volatile _SPM int *accum, const int K, const int KonePlus, const int shiftLeft) {
    int accum1;
    int accum2;
    for(int j=0; j<2; j++) {
        accum[0] = (KonePlus * x[j]);// >> 15;
        accum[1] = (K * abs(x[j])) >> 15;
        accum[1] = accum[1] + ((ONE_16b+shiftLeft) >> shiftLeft);
        accum[0] = accum[0]/accum[1];
        //reduce if it is poisitive only
        if (x[j] > 0) {
            y[j] = accum[0] - 1;
        }
        else {
            y[j] = accum[0];
        }
    }

    return 0;
}

__attribute__((always_inline))
int overdrive(volatile _SPM short *x, volatile _SPM short *y, volatile _SPM int *accum) {
    //THRESHOLD IS 1/3 = 0x2AAB
    //input abs:
    unsigned int x_abs[2];
    for(int j=0; j<2; j++) {
        x_abs[j] = abs(x[j]);
        if(x_abs[j] > (2 * 0x2AAB)) { // saturation : y = 1
            if (x[j] > 0) {
                y[j] = 0x7FFF;
            }
            else {
                y[j] = 0x8000;
            }
        }
        else {
            if(x_abs[j] > 0x2AAB) { // smooth overdrive: y = ( 3 - (2-3*x)^2 ) / 3;
                accum[j] = (0x17FFF * x_abs[j]) >> 15 ; // result is 1 sign + 17 bits
                accum[j] = 0xFFFF - accum[j];
                accum[j] = (accum[j] * accum[j]) >> 15;
                accum[j] = 0x17FFF - accum[j];
                accum[j] = (accum[j] * 0x2AAB) >> 15;
                if(x[j] > 0) { //positive
                    if(accum[j] > 32767) {
                        y[j] = 32767;
                    }
                    else {
                        y[j] = accum[j];
                    }
                }
                else { // negative
                    y[j] = -accum[j];
                }
            }
            else { // linear zone: y = 2*x
                y[j] = x[j] << 1;
            }
        }
    }

    return 0;
}
