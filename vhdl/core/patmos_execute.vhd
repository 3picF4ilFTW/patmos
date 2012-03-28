--TO DO: 
-- replcae pd with predicate_reg(pd) (number of predicate register that should be written with 0 or 1)


 -----------------------------------
 -- execution
 -----------------------------------

library ieee;
use ieee.numeric_std.all;
use ieee.std_logic_1164.all;
use work.patmos_type_package.all;


entity patmos_execute is
  port
  (
    clk                             : in std_logic;
    rst                             : in std_logic;
    din                       	     : in execution_in_type;
    dout                            : out execution_out_type
  );

end entity patmos_execute;

architecture arch of patmos_execute is

begin

  process(clk)
  begin
    if rising_edge(clk) then
      dout.reg_write_out <= din.reg_write_in;
      dout.mem_read_out <= din.mem_read_in;
      dout.mem_write_out <= din.mem_write_in;
      dout.mem_to_reg_out <= din.mem_to_reg_in;
      dout.alu_result_out <= din.alu_result_in;
      dout.mem_write_data_out <= din.mem_write_data_in;
      dout.write_back_reg_out <= din.write_back_reg_in;
    end if; 
  end process;
end arch;

