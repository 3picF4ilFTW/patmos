library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

package patmos_type_package is
  
  
  type instruction_type is (ALUi, ALU, NOP, SPC, LDT, STT, BEQ, STC);
  type STC_instruction_type is (SRES, SENS, SFREE);
  type pc_type is (PCNext, PCBranch);
  type ALU_inst_type is (ALUr, ALUu, ALUm, ALUc, ALUp);
  type SPC_type is (SPCn, SPCw, SPCt, SPCf);
  signal predicate_register_bank : unsigned(7 downto 0);
  type forwarding_type is (FWNOP, FWMEM, FWALU);
  type load_type is (lw, lh, lb, lhu, lbu, dlwh, dlbh, dlbu);

-------------------------------------------
-- in/out records
-------------------------------------------
constant        pc_length                   : integer := 32;
constant        instruction_word_length     : integer := 32;
-------------------------------------------
-- fetch/decode
-------------------------------------------
type fetch_in_type is record
    instruction                   :  unsigned(instruction_word_length - 1 downto 0); 
    pc                            :  unsigned(pc_length - 1 downto 0);
end record;
type fetch_out_type is record
		pc                            :  unsigned(pc_length - 1 downto 0);
		instruction                   :  unsigned(31 downto 0);
end record;

--------------------------------------------
-- decode/exec
--------------------------------------------
type decode_in_type is record
    operation                          : unsigned (31 downto 0);
		rs1_data_in                        : unsigned (31 downto 0);
    rs2_data_in                        : unsigned (31 downto 0);
    rs1_in                             : unsigned (4 downto 0);
    rs2_in                             : unsigned (4 downto 0);
 --   reg_write_in                       : std_logic;
    alu_src_in                         : std_logic;  
    rs1_data_in_special				   : unsigned(31 downto 0);   
    rs2_data_in_special				   : unsigned(31 downto 0);  
    head_in							   : unsigned(4 downto 0);
    tail_in							   : unsigned(4 downto 0);     
    stack_data_in   					: unsigned (31 downto 0);
end record;
type decode_out_type is record
		predicate_bit_out                   : std_logic;
    inst_type_out                       : instruction_type;
    ALU_function_type_out               : unsigned(3 downto 0);
    ALU_instruction_type_out            : ALU_inst_type;
    ALUi_immediate_out                  : unsigned(31 downto 0);
    pc_ctrl_gen_out                     : pc_type;
    wb_we_out                           : std_logic;
    rs1_out                             : unsigned (4 downto 0);
    rs2_out                             : unsigned (4 downto 0);
    rd_out                              : unsigned (4 downto 0);
    rs1_data_out                        : unsigned (31 downto 0);
    rs2_data_out                        : unsigned (31 downto 0);
    pd_out                              : unsigned (2 downto 0);
    sd_out                              : unsigned (3 downto 0);
    ss_out                              : unsigned (3 downto 0);
    ld_type_out                         : load_type;
    load_immediate_out                  : unsigned(6 downto 0);
    ld_function_type_out                : unsigned(1 downto 0);
    reg_write_out                       : std_logic;
    alu_src_out                         : std_logic; -- 0 for ALUi/ 1 for ALU
    mem_to_reg_out                      : std_logic; -- data to register file comes from alu or mem? 0 for alu and 1 for mem
    mem_read_out                        : std_logic;
    mem_write_out                       : std_logic;
    st_out							    : unsigned(3 downto 0);
    stack_data_out   					: unsigned (31 downto 0);
    STC_instruction_type_out			: STC_instruction_type;
    head_out							 : unsigned(4 downto 0);  
    tail_out							 : unsigned(4 downto 0);  
    stc_immediate_out					: unsigned (4 downto 0);
end record;
       
-------------------------------------------
-- execution
-------------------------------------------
type execution_in_type is record
    reg_write_in                        : std_logic;
    mem_read_in                         : std_logic;
    mem_write_in                        : std_logic;
    mem_to_reg_in                       : std_logic;
    alu_result_in                       : unsigned(31 downto 0);
    mem_write_data_in       	 	         : unsigned(31 downto 0);
    write_back_reg_in                   : unsigned(4 downto 0);
    tail_in								: unsigned(4 downto 0);
    head_in								: unsigned(4 downto 0);
end record;

type execution_out_type is record
    reg_write_out                     	 : std_logic;
    mem_read_out                        : std_logic;
    mem_write_out                       : std_logic;
    mem_to_reg_out                      : std_logic;
    alu_result_out                      : unsigned(31 downto 0);
    mem_write_data_out          	    : unsigned(31 downto 0);
    write_back_reg_out                  : unsigned(4 downto 0);
    tail_out							: unsigned(4 downto 0);
    head_out							: unsigned(4 downto 0);
end record;

------------------------------------------
-- control
------------------------------------------
type alu_in_type is record
   rs1                         : unsigned(31 downto 0);
   rs2                         : unsigned(31 downto 0);
   inst_type                   : instruction_type; 
   ALU_function_type           : unsigned(3 downto 0);
   ALU_instruction_type        : ALU_inst_type;
   stack_data_in			   : unsigned(31 downto 0);
   head_in						: unsigned(4 downto 0);
   STC_instruction_type			:STC_instruction_type;
   tail_in						: unsigned(4 downto 0);
   stc_immediate_in				: unsigned (4 downto 0);
end record;

type alu_out_type is record
  rd                          : unsigned(31 downto 0);
  head_out 		     			: unsigned(4 downto 0);
  tail_out					 : unsigned(4 downto 0);
  spill_out					: std_logic;
  --fill_out					 : std_logic;
end record;

------------------------------------------
-- mem
------------------------------------------
type mem_in_type is record
    reg_write_in                 : std_logic;
    mem_to_reg_in                : std_logic;
    mem_data_in                  : unsigned(31 downto 0); 
    alu_result_in                : unsigned(31 downto 0);
    write_back_reg_in            : unsigned(4 downto 0); 
    mem_write_data_in            : unsigned(31 downto 0);
end record;

type mem_out_type is record
    reg_write_out                 : std_logic;
    mem_to_reg_out                : std_logic;
    mem_data_out                  : unsigned(31 downto 0); 
    alu_result_out                : unsigned(31 downto 0);
    write_back_reg_out            : unsigned(4 downto 0); 
    mem_write_data_out            : unsigned(31 downto 0);
end record;
     


end patmos_type_package;



