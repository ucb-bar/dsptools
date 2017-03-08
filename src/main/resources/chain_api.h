// This header file defines the API for interacting with a DSP Chain

#ifndef __{{chain}}_GUARD
#define __{{chain}}_GUARD

#include <stdlib.h>
#include <stdbool.h>
{{#each blocks}}
{{#each addrs}}#define {{blockname}}_{{this.addrname}} {{addr}}
{{/each}}
{{/each}}
typedef struct sam_capture {
    unsigned long ctrl_base;
    unsigned long data_base;
    unsigned long n_samps;
    unsigned long start_addr;
    void *output;
    unsigned long output_size;
    unsigned long output_bytes_valid;
    bool wait_for_sync;
    bool check_writes;
    bool use_dma;
} sam_capture;
{{#each sam}}
#define {{samname}}_CTRL_BASE {{ctrl_base}}
#define {{samname}}_DATA_BASE {{data_base}}

static sam_capture {{samname}}_capture =
(sam_capture) {
    {{ctrl_base}}, // ctrl_base
    {{data_base}}, // data_base
    0L,            // n_samps
    0L,            // start_addr
    0L,            // output
    0L,            // output_size
    0L,            // output_bytes_valid
    1,             // wait_for_sync
    1,             // check_writes
    0,             // use_dma
};
{{/each}}
#define SAM_W_START_ADDR_OFFSET     {{samWStartAddrOffset}}
#define SAM_W_TARGET_COUNT_OFFSET   {{samWTargetCountOffset}}
#define SAM_W_TRIG_OFFSET           {{samWTrigOffset}}
#define SAM_W_WAIT_FOR_SYNC_OFFSET  {{samWWaitForSyncOffset}}

#define SAM_W_WRITE_COUNT_OFFSET    {{samWWriteCountOffset}}
#define SAM_W_PACKET_COUNT_OFFSET   {{samWPacketCountOffset}}
#define SAM_W_SYNC_ADDR_OFFSET      {{samWSyncAddrOffset}}

#define INITIATE_SAM_CAPTURE_ERROR (255)
#define NOT_IMPLEMENTED_ERROR      (254)

static inline void write_reg(unsigned long addr, unsigned long data)
{
    volatile unsigned long *ptr = (volatile unsigned long *) addr;
    *ptr = data;
}

static inline unsigned long read_reg(unsigned long addr)
{
    volatile unsigned long *ptr = (volatile unsigned long *) addr;
    return *ptr;
}

static void initiate_sam_capture(sam_capture* cap)
{
    unsigned long base = cap->ctrl_base;

    write_reg(base + SAM_W_START_ADDR_OFFSET,    cap->start_addr);
    write_reg(base + SAM_W_TARGET_COUNT_OFFSET,  cap->n_samps);
    write_reg(base + SAM_W_TRIG_OFFSET,          1L);
    write_reg(base + SAM_W_WAIT_FOR_SYNC_OFFSET, cap->wait_for_sync);

    if (cap -> check_writes) {
        if (read_reg(base + SAM_W_START_ADDR_OFFSET) !=
                cap->start_addr)
            exit(-INITIATE_SAM_CAPTURE_ERROR);
        if (read_reg(base + SAM_W_TARGET_COUNT_OFFSET) !=
                cap->n_samps)
            exit(-INITIATE_SAM_CAPTURE_ERROR);
        if (read_reg(base + SAM_W_TRIG_OFFSET) != 1L)
            exit(-INITIATE_SAM_CAPTURE_ERROR);
        if (read_reg(base + SAM_W_WAIT_FOR_SYNC_OFFSET) !=
                cap->wait_for_sync)
            exit(-INITIATE_SAM_CAPTURE_ERROR);
    }
}

static void get_sam_output(sam_capture* cap)
{
    unsigned long i;
    unsigned long ctrl_base = cap->ctrl_base;
    unsigned long data_base = cap->data_base;
    unsigned long *arr      = (unsigned long*)(cap->output);

    unsigned long write_count  =
        read_reg(ctrl_base + SAM_W_WRITE_COUNT_OFFSET);
    unsigned long packet_count =
        read_reg(ctrl_base + SAM_W_PACKET_COUNT_OFFSET);
    unsigned long sync_addr    =
        read_reg(ctrl_base + SAM_W_SYNC_ADDR_OFFSET);

    if (cap->use_dma) {
        exit(-NOT_IMPLEMENTED_ERROR);
    } else {
        for (i=0L; i<write_count; i++) {
            arr[sync_addr + i] =
                read_reg(data_base + sync_addr);
        }
    }
}

// TODO PG/LA
#endif
