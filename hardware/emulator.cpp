#include <fstream>
#include <iostream>

#include <stdio.h>
#include <unistd.h>
#include <gelf.h>
#include <libelf.h>

#include "Patmos.h"

istream *in = &cin;
ostream *out = &cout;
ofstream cmiss;

#define OCMEM_ADDR_BITS 16

#define SRAM_ADDR_BITS 19 // 2MB
static uint32_t ssram_buf [1 << SRAM_ADDR_BITS];
#define SRAM_CYCLES 3
  
/// Read an elf executable image into the on-chip memories
static val_t readelf(istream &is, Patmos_t *c)
{
  vector<unsigned char> elfbuf;
  elfbuf.reserve(1 << 20);

  // read the whole stream.
  while (!is.eof())
  {
	char buf[1024];

    // read into buffer
    is.read(&buf[0], sizeof(buf));

    // check how much was read
    streamsize count = is.gcount();
    assert(count <= 1024);

    // write into main memory
    for(unsigned i = 0; i < count; i++)
      elfbuf.push_back(buf[i]);
  }

  // check libelf version
  elf_version(EV_CURRENT);

  // open elf binary
  Elf *elf = elf_memory((char*)&elfbuf[0], elfbuf.size());
  assert(elf);

  // check file kind
  Elf_Kind ek = elf_kind(elf);
  if (ek != ELF_K_ELF) {
    cerr << "readelf: ELF file must be of kind ELF.\n";
    exit(EXIT_FAILURE);
  }

  // get elf header
  GElf_Ehdr hdr;
  GElf_Ehdr *tmphdr = gelf_getehdr(elf, &hdr);
  assert(tmphdr);

  if (hdr.e_machine != 0xBEEB) {
    cerr << "readelf: unsupported architecture: ELF file is not a Patmos ELF file.\n";
    exit(EXIT_FAILURE);
  }
  
  // check class
  int ec = gelf_getclass(elf);
  if (ec != ELFCLASS32) {
    cerr << "readelf: unsupported architecture: ELF file is not a 32bit Patmos ELF file.\n";
    exit(EXIT_FAILURE);
  }

  // get program headers
  size_t n;
  int ntmp = elf_getphdrnum (elf, &n);
  assert(ntmp == 0);

  for(size_t i = 0; i < n; i++) {
    // get program header
    GElf_Phdr phdr;
    GElf_Phdr *phdrtmp = gelf_getphdr(elf, i, &phdr);
    assert(phdrtmp);

    if (phdr.p_type == PT_LOAD) {
      // some assertions
      assert(phdr.p_vaddr == phdr.p_paddr);
      assert(phdr.p_filesz <= phdr.p_memsz);

      // copy from the buffer into the on-chip memories
	  for (size_t k = 0; k < phdr.p_memsz; k++) {

		if (((phdr.p_paddr + k) >> OCMEM_ADDR_BITS) == 0x1 && 
			((phdr.p_paddr + k) & 0x3) == 0) {
		  // Address maps to ISPM and is at a word boundary
		  val_t word = k >= phdr.p_filesz ? 0 :
			(((val_t)elfbuf[phdr.p_offset + k + 0] << 24) |
			 ((val_t)elfbuf[phdr.p_offset + k + 1] << 16) |
			 ((val_t)elfbuf[phdr.p_offset + k + 2] << 8) |
			 ((val_t)elfbuf[phdr.p_offset + k + 3] << 0));
		  val_t addr = ((phdr.p_paddr + k) - (0x1 << OCMEM_ADDR_BITS)) >> 3;

		  unsigned size = (sizeof(c->Patmos_core_fetch_memEven__mem.contents) / 
						   sizeof(c->Patmos_core_fetch_memEven__mem.contents[0]));
		  assert(addr < size && "Instructions mapped to ISPM exceed size");

		  // Write to even or odd block
		  if (((phdr.p_paddr + k) & 0x4) == 0) {
			c->Patmos_core_fetch_memEven__mem.put(addr, word);
		  } else {
			c->Patmos_core_fetch_memOdd__mem.put(addr, word);
		  }
		}

		if (((phdr.p_paddr + k) & 0x3) == 0) {
		  // Address maps to SRAM and is at a word boundary
		  val_t word = k >= phdr.p_filesz ? 0 :
			(((val_t)elfbuf[phdr.p_offset + k + 0] << 24) |
			 ((val_t)elfbuf[phdr.p_offset + k + 1] << 16) |
			 ((val_t)elfbuf[phdr.p_offset + k + 2] << 8) |
			 ((val_t)elfbuf[phdr.p_offset + k + 3] << 0));

		  val_t addr = ((phdr.p_paddr + k) >> 2);
		  ssram_buf[addr] = word;
		}
	  }
    }
  }

  // get entry point
  val_t entry = hdr.e_entry;

  elf_end(elf);

  return entry;
}

static void print_state(Patmos_t *c) {
	sval_t pc = c->Patmos_core_memory__io_memwb_pc.to_ulong();
	*out << (pc - 2) << " - ";

	for (unsigned i = 0; i < 32; i++) {
	  *out << c->Patmos_core_decode_rf__rf.get(i).to_ulong() << " ";
	}

	*out << endl;
}

static void extSsramSim(Patmos_t *c) {
  static uint32_t addr_cnt;
  static uint32_t address;
  static uint32_t counter;

  // *out << "noe:" << c->Patmos__io_sramPins_ram_out_noe.to_ulong() 
  // 	   << " nadv: " << c->Patmos__io_sramPins_ram_out_nadv.to_ulong()
  // 	   << " nadsc:" << c->Patmos__io_sramPins_ram_out_nadsc.to_ulong()
  // 	   << " addr:" << c->Patmos__io_sramPins_ram_out_addr.to_ulong() << "\n";

  if (c->Patmos__io_sramPins_ram_out_nadsc.to_ulong() == 0) {
    address = c->Patmos__io_sramPins_ram_out_addr.to_ulong();
    addr_cnt = c->Patmos__io_sramPins_ram_out_addr.to_ulong();
    counter = 0;
  }
  if (c->Patmos__io_sramPins_ram_out_nadv.to_ulong() == 0) {
    addr_cnt++;
  }
  if (c->Patmos__io_sramPins_ram_out_noe.to_ulong() == 0) {
    counter++;
    if (counter >= SRAM_CYCLES) {
      c->Patmos__io_sramPins_ram_in_din = ssram_buf[address];
      if (address <= addr_cnt) {
        address++;
      }
    }
  }
  if (c->Patmos__io_sramPins_ram_out_nbwe.to_ulong() == 0) {
	uint32_t nbw = c->Patmos__io_sramPins_ram_out_nbw.to_ulong();
	uint32_t mask = 0x00000000;
	for (unsigned i = 0; i < 4; i++) {
	  if ((nbw & (1 << i)) == 0) {
		mask |= 0xff << (i*8);
	  }
	}

	ssram_buf[address] &= ~mask;
	ssram_buf[address] |= mask & c->Patmos__io_sramPins_ram_out_dout.to_ulong();

	if (address <= addr_cnt) {
	  address++;
	}
  }

}

static void mcacheStat(Patmos_t *c, bool halt) {
  static uint cache_miss = 0;
  static uint cache_hits = 0;
  static uint exec_cycles = 0;
  static uint cache_stall_cycles = 0;
  //count all cycles till the program terminats
  exec_cycles++;
  //count everytime a new method is written to the cache
  if (c->Patmos_core_mcache_mcachectrl__io_mcache_ctrlrepl_w_tag.to_bool() == true) {
    cache_miss++;
  }
  //everytime a method is called from the cache, todo: find a better way to measure hits
  if (c->Patmos_core_fetch__io_memfe_doCallRet.to_bool() == true &&
      c->Patmos_core_mcache_mcacherepl__io_mcache_replctrl_hit.to_bool() == true &&
      c->Patmos_core_mcache_mcachectrl__mcache_state.to_ulong() == 1 &&
      c->Patmos_core_mcache__io_ena_in.to_bool() == true &&
      c->Patmos_core_mcache_mcachectrl__io_mcache_ctrlrepl_instr_stall.to_bool() == false) {
    cache_hits++;
  }
  //pipeline stalls caused by the mcache
  if (c->Patmos_core_mcache__io_ena_out.to_bool() == false) {
    cache_stall_cycles++;
  }
  //program terminats, write to output
  if (halt == true) {
    *out << "exec_cycles:" << exec_cycles
         << " cache_hits:" << cache_hits
         << " cache_misses:" << cache_miss
         <<  " cache_stall_cycles:" << cache_stall_cycles << "\n";
  }
}

static void usage(ostream &out, const char *name) {
  out << "Usage: " << name
      << " [-q|-r] [-u|-n] [-v] [-p] [-l cycles] [-I file] [-O file] [-h] [file]" << endl;
}

static void help(ostream &out) {
  out << endl << "Options:" << endl
      << "  -q            Do not print register values in each cycles" << endl
      << "  -r            Print register values in each cycles" << endl
      << "  -u            Print UART output" << endl
      << "  -n            Do not print UART output" << endl
      << "  -v            Dump wave forms file \"Patmos.vcd\"" << endl
      << "  -p            Print method cache statistics" << endl
      << "  -l <N>        Stop after <N> cycles" << endl
      << "  -I <file>     Read input from file <file> [unused]" << endl
      << "  -O <file>     Write output from file <file>" << endl
      << "  -h            Print this help" << endl;
}

int main (int argc, char* argv[]) {
  Patmos_t* c = new Patmos_t();

  int opt;
  int lim = -1;
  bool vcd = false;

  // MS: what it the usage of disabling the UART?
  // WP: output from the UART can mess up trace and cause discrepancies with simulator
  bool uart = true;
  bool keys = false;
  bool quiet = true;
  bool print_stat = false;

  while ((opt = getopt(argc, argv, "qurnvpl:I:O:h")) != -1) {
	switch (opt) {
	// MS: q and u should go away, but tests in bench need updates first
	case 'q':
	  quiet = true;
	  break;
	case 'r':
	  quiet = false;
	  break;
	case 'u':
	  uart = true;
	  break;
	case 'k':
	  keys = true;
	case 'n':
	  uart = false;
	  break;
	case 'v':
	  vcd = true;
	  break;
	case 'p':
	  print_stat = true;
	  break;
	case 'l':
	  lim = atoi(optarg);
	  break;
	case 'I':
	  if (strcmp(optarg, "-") == 0) {
		in = &cin;
	  } else {
		in = new ifstream(optarg);
		if (!in->good()) {
		  cerr << argv[0] << "error: Cannot open input file " << optarg << endl;
		  exit(EXIT_FAILURE);
		}
	  }
	  break;
	case 'O':
	  if (strcmp(optarg, "-") == 0) {
		out = &cout;
	  } else {
		out = new ofstream(optarg);
		if (!out->good()) {
		  cerr << argv[0] << ": error: Cannot open output file " << optarg << endl;
		  exit(EXIT_FAILURE);
		}
	  }
	  break;
	case 'h':
	  usage(cout, argv[0]);
	  help(cout);
	  exit(EXIT_SUCCESS);
	default: /* '?' */
	  usage(cerr, argv[0]);
	  exit(EXIT_FAILURE);
	}
  }

  c->init();

  srand(0);

  val_t entry = 0;
  if (optind < argc) {
	ifstream *fs = new ifstream(argv[optind]);
	if (!fs->good()) {
	  cerr << argv[0] << ": error: Cannot open elf file " << argv[optind] << endl;
	  exit(EXIT_FAILURE);
	}
	entry = readelf(*fs, c);
  }

  FILE *f = vcd ? fopen("Patmos.vcd", "w") : NULL;
  
  if (!quiet) {
	*out << "Patmos start" << endl;
  }

  // Assert reset for a few cycles
  for(int i = 0; i < 5; i++) {
    dat_t<1> reset = LIT<1>(1);
    c->clock_lo(reset);
    c->clock_hi(reset);
  }

  if (entry != 0) {
    if (entry >= 0x20000) {
      c->Patmos_core_fetch__pcReg = -1;
      c->Patmos_core_mcache_mcacherepl__hitReg = 0;
      c->Patmos_core_mcache_mcacherepl__selMCacheReg = 1;
      c->Patmos_core_fetch__relBaseReg = 0;
      c->Patmos_core_fetch__relocReg = (entry >> 2) - 1;
      //init linked list for lru replacement
      // c->Patmos_core_mcache_mcachectrl__addrReg = 0;
      // c->Patmos_core_mcache_mcacherepl__lru_list_prev_0 = 1;
      // c->Patmos_core_mcache_mcacherepl__lru_list_prev_1 = 2;
      // c->Patmos_core_mcache_mcacherepl__lru_list_prev_2 = 3;
      // c->Patmos_core_mcache_mcacherepl__lru_list_prev_3 = 0;
      // c->Patmos_core_mcache_mcacherepl__lru_list_next_0 = 3;
      // c->Patmos_core_mcache_mcacherepl__lru_list_next_1 = 0;
      // c->Patmos_core_mcache_mcacherepl__lru_list_next_2 = 1;
      // c->Patmos_core_mcache_mcacherepl__lru_list_next_3 = 2;
      //init for icache
      // c->Patmos_core_mcache_icacherepl__selICacheReg = 1;
      // c->Patmos_core_fetch__pcReg = 0;
    }
    else {
      // pcReg for ispm starts at entry point - ispm base
      c->Patmos_core_fetch__pcReg = ((entry - 0x10000) >> 2) - 1;
      c->Patmos_core_mcache_mcacherepl__selIspmReg = 1;
      c->Patmos_core_fetch__relBaseReg = (entry - 0x10000) >> 2;
      c->Patmos_core_fetch__relocReg = 0x10000 >> 2;
      //init for icache
      // c->Patmos_core_mcache_icacherepl__selIspmReg = 1;
    }
    c->Patmos_core_execute__baseReg = entry;
    c->Patmos_core_mcache_mcachectrl__callRetBaseReg = (entry >> 2);
    c->Patmos_core_mcache_mcacherepl__callRetBaseReg = (entry >> 2);
    //init for icache
    // c->Patmos_core_mcache_icachectrl__callRetBaseReg = (entry >> 2);
    // c->Patmos_core_mcache_icacherepl__callRetBaseReg = (entry >> 2);
  }

  // Main emulation loop
  bool halt = false;

  for (int t = 0; lim < 0 || t < lim; t++) {
    dat_t<1> reset = LIT<1>(0);

    c->clock_lo(reset);
    c->clock_hi(reset);

    extSsramSim(c);

	if (vcd) {
	  c->dump(f, t);
	}

	if (keys) {
	  // if ((rand() % 0x10000) == 0) {
	  // 	c->Patmos__io_key = rand();
	  // }
	}

	if (uart) {
	  // Pass on data from UART
	  if (c->Patmos_core_iocomp_Uart__io_ocp_M_Cmd.to_ulong() == 0x1
	  	  && (c->Patmos_core_iocomp_Uart__io_ocp_M_Addr.to_ulong() & 0xff) == 0x04) {
	  	*out << (char)c->Patmos_core_iocomp_Uart__io_ocp_M_Data.to_ulong();
	  }
	}

	if (!quiet && c->Patmos_core__enableReg.to_bool()) {
	  print_state(c);
	}
	
	// Return to address 0 halts the execution after one more iteration
	if (halt) {
	  break;
	}
	if ((c->Patmos_core_memory__memReg_mem_brcf.to_bool()
		 || c->Patmos_core_memory__memReg_mem_ret.to_bool())
		&& c->Patmos_core_mcache_mcachectrl__callRetBaseReg.to_ulong() == 0) {
	  halt = true;
	}
	//for icache
	// if (c->Patmos_core_memory__memReg_mem_ret.to_bool()
	// 	&& c->Patmos_core_mcache_icacherepl__callRetBaseReg.to_ulong() == 0) {
	//   halt = true;
	// }
	if (print_stat == true) {
	  mcacheStat(c, halt);
	}

  }

  // TODO: adapt comparison tool so this can be removed
  if (!quiet) {
	*out << "PASSED" << endl;
  }

  // Pass on return value from processor
  return c->Patmos_core_decode_rf__rf.get(1).to_ulong();
}
