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
    volatile _UNCACHED int *allocsDoneP = inArgs[1];

    volatile _UNCACHED int *audioValuesP = inArgs[3];

    /*
      AUDIO STUFF HERE
    */

    /*
    //init and allocate
    struct AudioFX audio1a;
    struct AudioFX *audio1aP = &audio1a;
    alloc_dry_vars(audio1aP, IN_NOC, OUT_NOC, NO_FIRST, NO_LAST); //from 0, to 0, not 1st, not last

    qpd_t * chanRecv1 = audio_connect_from_core(0, audio1aP); // effect audio1a from core 0
    qpd_t * chanSend1 = audio_connect_to_core(audio1aP, 0); // effect audio1a to core 0
    */
    // wait until all cores are ready
    //allocsDoneP[*audio1aP->cpuid] = 1;
    allocsDoneP[1] = 1;
    while(allocsDoneP[0] == 0);

    /*
    // Initialize the communication channels
    int nocret = mp_init_ports();

    //loop
    //for(int i=0; i<3; i++) {
    while(*exitP == 0) {
        mp_recv(chanRecv1, 0);
        //process
        audio_dry(audio1aP);

        / *
        volatile _SPM short * xP = (volatile _SPM short *)*(volatile _SPM unsigned int *)*audio1aP->x_pnt;
        audioValuesP[i] = xP[0];
        audioValuesP[i] = (audioValuesP[i] & 0xFFFF) | ( (xP[1] & 0xFFFF) << 16 );
        * /

        mp_ack(chanRecv1, 0);

        mp_send(chanSend1, 0);
    }
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
    int allocsDone[2] = {0, 0};
    volatile _UNCACHED int *exitP = (volatile _UNCACHED int *) &exit;
    volatile _UNCACHED int *allocsDoneP = (volatile _UNCACHED int *) &allocsDone;
    volatile _UNCACHED int (*thread1_args[6]);
    thread1_args[0] = exitP;
    thread1_args[1] = allocsDoneP;

    int audioValues[3] = {0, 0, 0};
    volatile _UNCACHED int *audioValuesP = (volatile _UNCACHED int *) &audioValues[0];
    thread1_args[3] = audioValuesP;

    printf("starting thread and NoC channels...\n");
    //set thread function and start thread
    corethread_create(&threadOne, &thread1, (void*) thread1_args);

    /*
    qpd_t * chan2 = mp_create_qport(MP_CHAN_2_ID, SINK,
        MP_CHAN_2_MSG_SIZE, MP_CHAN_2_NUM_BUF);

    */

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

    // create effects and connect
    struct AudioFX audio0a;
    struct AudioFX *audio0aP = &audio0a;
    // from 0 (same), to 0 (same), is 1st, is not last
    alloc_dry_vars(audio0aP, NO_NOC, NO_NOC, 1, 1, FIRST, NO_LAST); //INSIZE=1, OUTSIZE=1

    struct AudioFX audio0b;
    struct AudioFX *audio0bP = &audio0b;
    //from 0 (same), to 1, is not 1st, is not last
    //alloc_dry_vars(audio0bP, NO_IN_NOC, OUT_NOC, NO_FIRST, NO_LAST);
    alloc_dry_vars(audio0bP, NO_NOC, NO_NOC, 1, 1, NO_FIRST, LAST); //INSIZE=1, OUTSIZE=1

    /*
    struct AudioFX audio0c;
    struct AudioFX *audio0cP = &audio0c;
    //from 1, to 0 (same), is not 1st, is last
    //alloc_dry_vars(audio0cP, IN_NOC, NO_OUT_NOC, NO_FIRST, LAST);
    */
    audio_connect_fx(audio0aP, audio0bP); //effects on same core
    //qpd_t * chanSend0 = audio_connect_to_core(audio0bP, 1); // effect audio0b to core 1
    //qpd_t * chanRecv0 = audio_connect_from_core(1, audio0cP); //effect audio0c from core 1

    // wait until all cores are ready
    allocsDoneP[*audio0bP->cpuid] = 1;
    while(allocsDoneP[1] == 0);

    /*
    // Initialize the communication channels
    int nocret = mp_init_ports();
    if(nocret == 1) {
        printf("Thread and NoC initialised correctly\n");
    }
    else {
        printf("ERROR: Problem with NoC initialisation\n");
    }
    */

    //CPU cycles stuff
    //int CPUcycles[1000] = {0};
    //int cpu_pnt = 0;

    /*
    //loop
    //for(int i=0; i<3; i++) {
    while(*keyReg != 3) {
        audioIn(audio0aP);
        //process
        audio_dry(audio0aP);

        //printf("in values: %d, %d\n", audio0bP->x[0], audio0bP->x[1]);

        audio_dry(audio0bP);

        / *
        printf("y_pnt points to 0x%x\n", *audio0bP->y_pnt);
        printf("*y_pnt points to 0x%x\n", *(volatile _SPM unsigned int *)*audio0bP->y_pnt);
        printf("data at **y_pnt is %d, %d\n", *(volatile _SPM short *)*(volatile _SPM unsigned int *)*audio0bP->y_pnt, *(volatile _SPM short *)((*(volatile _SPM unsigned int *)*audio0bP->y_pnt)+2));

        printf("write buffer address: 0x%x\n", (int)chanSend0->write_buf);
        printf("write buffer data: %d, %d\n", (int)*((volatile _SPM short *)chanSend0->write_buf), (int)*((volatile _SPM short *)chanSend0->write_buf+ 1));
        * /
        mp_send(chanSend0, 0);

        mp_recv(chanRecv0, 0);
        / *
        volatile _SPM short * xP = (volatile _SPM short *)*(volatile _SPM unsigned int *)*audio0cP->x_pnt;
        printf("RECEIVED FROM CORE 1: %d, %d\n", xP[0], xP[1]);
        * /
        audio_dry(audio0cP);

        mp_ack(chanRecv0, 0);

        audioOut(audio0cP);
    }
    */

    /*
    for(int i=0; i<3; i++) {
        printf("received @ %d: %d, %d\n", i, (short)(audioValuesP[i] & 0xFFFF), (short)( (audioValuesP[i] & 0xFFFF0000) >> 16 ));
    }
    */



    while(*keyReg != 3) {
        audio_dry(audio0aP);
        audio_dry(audio0bP);

        /*
        audioIn(audio0aP);
        audio_dry(audio0aP);
        audio_dry(audio0bP);
        mp_send(chanSend, 0);
        */

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
    printf("SOME CARACS:\n");
    printf("AUDIO1:\n");
    printf("CPUID = %d\n", *audio1P->cpuid);
    printf("IS_FST = %d\n", *audio1P->is_fst);
    printf("IS_LST = %d\n", *audio1P->is_lst);
    printf("IN_CON = %d\n", *audio1P->in_con);
    printf("OUT_CON = %d\n", *audio1P->out_con);
    printf("X_PNT = 0x%x\n", *audio1P->x_pnt);
    printf("Y_PNT = 0x%x\n", *audio1P->y_pnt);
    printf("AUDIO2:\n");
    printf("CPUID = %d\n", *audio2P->cpuid);
    printf("IS_FST = %d\n", *audio2P->is_fst);
    printf("IS_LST = %d\n", *audio2P->is_lst);
    printf("IN_CON = %d\n", *audio2P->in_con);
    printf("OUT_CON = %d\n", *audio2P->out_con);
    printf("X_PNT = 0x%x\n", *audio2P->x_pnt);
    printf("Y_PNT = 0x%x\n", *audio2P->y_pnt);
    */

    //exit stuff
    printf("exit here!\n");
    exit = 1;
    printf("waiting for thread 1 to finish...\n");

    /*
    for(int i=1; i<1000; i++) {
        printf("%d\n", (CPUcycles[i]-CPUcycles[i-1]));
    }
    */

    //join with thread 1
    int *retval;
    corethread_join(threadOne, (void **)&retval);
    printf("thread 1 finished!\n");

    return 0;
}
