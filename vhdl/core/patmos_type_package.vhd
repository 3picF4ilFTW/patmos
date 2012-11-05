-- 
-- Copyright Technical University of Denmark. All rights reserved.
-- This file is part of the time-predictable VLIW Patmos.
-- 
-- Redistribution and use in source and binary forms, with or without
-- modification, are permitted provided that the following conditions are met:
-- 
--    1. Redistributions of source code must retain the above copyright notice,
--       this list of conditions and the following disclaimer.
-- 
--    2. Redistributions in binary form must reproduce the above copyright
--       notice, this list of conditions and the following disclaimer in the
--       documentation and/or other materials provided with the distribution.
-- 
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER ``AS IS'' AND ANY EXPRESS
-- OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
-- OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
-- NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY
-- DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
-- (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
-- LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
-- ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
-- (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
-- THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
-- 
-- The views and conclusions contained in the software and documentation are
-- those of the authors and should not be interpreted as representing official
-- policies, either expressed or implied, of the copyright holder.
-- 


--------------------------------------------------------------------------------
-- Short descripton.
--
-- Author: Sahar Abbaspour
-- Author: Martin Schoeberl (martin@jopdesign.com)
--------------------------------------------------------------------------------


library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

package patmos_type_package is
	type STC_instruction_type is (NONE, SRES, SENS, SFREE);
	type pc_type is (PCNext, PCBranch);
	type ALU_inst_type is (NONE, ALUr, ALUu, ALUm, ALUc, ALUp);
--	type STT_inst_type is (NONE, SWS, SWL, SWC, SWM, SHM, SBM, SHS, SHL, SHC, SBS, SBL, SBC); -- all stores
--	type LDT_inst_type is (NONE, LWM, LHM, LBM, LHUM, LBUM, LWS, LHS, LBS, LHUS, LBUS, LWL, LHL, LBL, LHUL, LBUL, LWC, LHC, LBC, LHUC, LBUC);
	type SPC_type is (NONE, SPCn, SPCw, SPCt, SPCf);
	type load_type is (NONE, lw, lh, lb, lhu, lbu, dlwh, dlbh, dlbu);
	type address_type is (word, half, byte);
	type function_type_alu is (pat_add, pat_sub, pat_rsub, pat_sl, pat_sr, pat_sra, pat_or, pat_and, pat_rl, pat_rr, 
		pat_xor, pat_nor, pat_shadd, pat_shadd2);
	type function_type_alu_u is (pat_sext8, pat_sext16, pat_zext16, pat_abs);
	type function_type_alu_p is (pat_por, pat_pand, pat_pxor, pat_pnor);
	type function_type_alu_cmp is (pat_cmpeq, pat_cmpneq, pat_cmplt, pat_cmple, pat_cmpult, pat_cmpule, pat_btest);
	type isntrucion is (st, ld, nop);
	-------------------------------------------
	-- in/out records
	-------------------------------------------
	constant pc_length               : integer := 32;
	constant instruction_word_length : integer := 32;
	-------------------------------------------
	-- fetch/decode
	-------------------------------------------
	type fetch_in_type is record
		pc : std_logic_vector(pc_length - 1 downto 0);
	end record;
	type fetch_out_type is record
		pc          : std_logic_vector(pc_length - 1 downto 0);
		instruction : std_logic_vector(31 downto 0);
		instr_b     : std_logic_vector(31 downto 0);
		b_valid     : std_logic;
	end record;

	--------------------------------------------
	-- decode/exec
	--------------------------------------------
	type decode_in_type is record
		operation   : std_logic_vector(31 downto 0);
		pc			: std_logic_vector(pc_length - 1 downto 0);
		instr_b     : std_logic_vector(31 downto 0);
		rs1_data_in : std_logic_vector(31 downto 0);
		rs2_data_in : std_logic_vector(31 downto 0);
	end record;
	type decode_out_type is record
		lm_write : std_logic;
		lm_read	 : std_logic;
		imm       : std_logic_vector(31 downto 0);
		instr_cmp : std_logic;

		predicate_bit       : std_logic;
		predicate_condition      : std_logic_vector(2 downto 0);
		ps1                  : std_logic_vector(3 downto 0);
		ps2                  : std_logic_vector(3 downto 0);
		rs1                  : std_logic_vector(4 downto 0);
		rs2                  : std_logic_vector(4 downto 0);
		rd                   : std_logic_vector(4 downto 0);
		rs1_data             : std_logic_vector(31 downto 0);
		rs2_data             : std_logic_vector(31 downto 0);
		pd                   : std_logic_vector(3 downto 0);
		reg_write            : std_logic;
		alu_src              : std_logic; -- 0 for ALUi/ 1 for ALU
		mem_to_reg           : std_logic; -- data to register file comes from alu or mem? 0 for alu and 1 for mem
		BC						: std_logic;
		pat_function_type_alu_cmp	: function_type_alu_cmp;
		pat_function_type_alu      :function_type_alu;
		pat_function_type_alu_u      :function_type_alu_u;
		pat_function_type_alu_p      :function_type_alu_p;
		is_predicate_inst			 : std_logic;
		adrs_type				 : address_type;
		alu_alu_u				: std_logic;
		s_u						: std_logic;
		pc					: std_logic_vector(pc_length - 1 downto 0);
		
		inst				:isntrucion;			
		--		mem_write_out            : std_logic;
--		st_out                   : std_logic_vector(3 downto 0);
--		sc_write_out             : std_logic;	
	end record;



	type result_type is record
		value  : std_logic_vector(31 downto 0);
		reg_nr : std_logic_vector(4 downto 0);
		valid  : std_logic;
	end record;

	-------------------------------------------
	-- execution
	-------------------------------------------

	type execution_out_type is record
		alu_result               : std_logic_vector(31 downto 0);
		adrs					 : std_logic_vector(31 downto 0);
		predicate                : std_logic_vector(7 downto 0);
--		result                   : result_type;
		alu_result_reg           : std_logic_vector(31 downto 0);
		adrs_reg     	      : std_logic_vector(31 downto 0);
		reg_write            : std_logic;
		mem_to_reg           : std_logic;
		write_back_reg       : std_logic_vector(4 downto 0);

		lm_read  : std_logic;
		lm_write : std_logic;
--		sc_read_out  : std_logic;
--		sc_write_out : std_logic;
		mem_write_data : std_logic_vector(31 downto 0); 
		
		adrs_type		    :  address_type;
		--unregistered outputs
		lm_read_out_not_reg	: std_logic;
		lm_write_out_not_reg : std_logic;
		sc_read_out_not_reg  : std_logic;
		sc_write_out_not_reg : std_logic;
		address_not_reg		: std_logic_vector(31 downto 0);
		pc					: std_logic_vector(pc_length - 1 downto 0);
	end record;


	------------------------------------------
	-- mem
	------------------------------------------

	type mem_out_type is record
		result             : result_type;
		data_out           : std_logic_vector(31 downto 0); -- forwarding
		-- following is forwarding 
		reg_write_out      : std_logic;
		write_back_reg_out : std_logic_vector(4 downto 0);
		data_mem_data_out  : std_logic_vector(31 downto 0); -- this is from memory it is used later to select between output of mem or IO
		data  : std_logic_vector(31 downto 0); -- to register file
		
	end record;

	type patmos_stack_cache_ctrl_in is record
		stc_immediate_in : std_logic_vector(4 downto 0);
		instruction      : STC_instruction_type; -- from decode
	--		st_in				: std_logic_vector(31 downto 0);
	end record;

	type patmos_stack_cache_ctrl_out is record
		stall         : std_logic;
		head_tail     : std_logic_vector(4 downto 0); -- connect to stack cache
		spill_fill    : std_logic;
		st_out        : std_logic_vector(31 downto 0);
		reg_write_out : std_logic;
	end record;

	type patmos_stack_cache_in is record
		head_tail    : std_logic_vector(4 downto 0);
		din_from_mem : std_logic_vector(31 downto 0); -- mem interface
		din_from_cpu : std_logic_vector(31 downto 0);
		spill_fill   : std_logic;
		write_enable : std_logic;
		address      : std_logic_vector(4 downto 0);
	end record;

	type patmos_stack_cache_out is record
		dout_to_mem : std_logic_vector(31 downto 0); -- mem interface
		dout_to_cpu : std_logic_vector(31 downto 0);
	end record;

	type instruction_memory_in_type is record
		address      : std_logic_vector(31 downto 0);
		inst_in      : std_logic_vector(31 downto 0); -- from boot loader
		read_enable  : std_logic;
		write_enable : std_logic;
	end record;

	type instruction_memory_out_type is record
		inst_out : std_logic_vector(31 downto 0); -- to fetch
	end record;

	type instruction_rom_in_type is record
		address : std_logic_vector(7 downto 0);
	end record;

	type instruction_rom_out_type is record
		q : std_logic_vector(31 downto 0);
	end record;


	-- Memory Mapped I/O
	-- Edgar: io_none is not stricltly needed as there are read/write enables
	type io_device_type is (io_none, io_uart, io_sdram, io_leds, io_counter, io_memmory);

	type io_info_type is record
		-- full address, device would use dedicated slice
		address            : std_logic_vector(31 downto 0);
		-- enabled device, used in output mux
		device             : io_device_type;
		-- read/write enable (just a copy from the instruction decoding)
		rd, wr             : std_logic;
		-- one hot enable for individual device
		mem_en             : std_logic;
		led_en             : std_logic;
		uart_en            : std_logic;
		sdram_en           : std_logic;
		counter_en         : std_logic;
		-- some other signals present in the decoding code (Edgar: was there before refactoring, so I left them)
		instruction_mem_wr : std_logic;
		stack_cache_wr     : std_logic;
	end record;

end patmos_type_package;

