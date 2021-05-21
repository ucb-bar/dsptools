#include "dma-ext.h"
#include "memcpy.h"

#include <stdio.h>
#include <stdint.h>
//#include <sys/mman.h>

#define PAGE_SHIFT 12
#define PAGE_SIZE 4096

static unsigned int page_faults;

static inline void pagein(unsigned long vpn, int npages)
{
	void *ptr = (void *)(vpn << PAGE_SHIFT);
	/*if (mlock(ptr, npages * PAGE_SIZE)) {
		perror("mlock()");
		abort();
	}
	if (munlock(ptr, npages * PAGE_SIZE)) {
		perror("munlock()");
		abort();
	}*/
	page_faults += npages;
}

static inline void memcpy_wait(void)
{
	unsigned long vpn;
	int status;

	for (;;) {
		asm volatile ("fence");
		status = dma_read_cr(RESP_STATUS);
		if (status == NO_ERROR)
			break;
		if (status != SRC_PAGE_FAULT && status != DST_PAGE_FAULT) {
			printf("Unhandleable error %d", status);
			exit(-(status));
		}
		vpn = dma_read_cr(RESP_VPN);
		pagein(vpn, 1);
		dma_resume();
	}
}

void test_memcpy(void *dst, void *src, size_t size)
{
	uintptr_t firstvpn = ((uintptr_t) dst) >> PAGE_SHIFT;
	uintptr_t lastvpn = ((uintptr_t) (dst + size - 1)) >> PAGE_SHIFT;
	size_t npages = lastvpn - firstvpn + 1;

	page_faults = 0;

	pagein(firstvpn, npages);

	dma_write_cr(SEGMENT_SIZE, size);
	dma_write_cr(NSEGMENTS, 1);
	dma_transfer(dst, src);
	memcpy_wait();

	//printf("# page faults: %u\n", page_faults);
}
