---------------------------------
-- pc generation
---------------------------------
library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;
use work.patmos_type_package.all;

entity patmos_pc_generator is 
    generic
    (
        pc_length                   : integer := 32
    );
    
    port
    (
         clk                        : in std_logic;
         rst                        : in std_logic;
         pc                         : in unsigned(pc_length - 1 downto 0);-- (others => '0');
         pc_out                     : out unsigned(pc_length - 1 downto 0)
    );
end entity patmos_pc_generator;

architecture arch of patmos_pc_generator is

begin
  
	   
    pc_gen: process (clk, rst)
    begin
     if ((rst = '1')) then
           pc_out <= (others => '0');  
      elsif (rising_edge(clk)) then
           pc_out <= pc;
      end if;
      
    end process pc_gen ;        

end arch;
-------------------------------------
-- sign extension for branch
-------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

entity sign_extension is
  port 
  (
    immediate                       : in unsigned(21 downto 0);
    sign_extended_immediate         : out unsigned(31 downto 0)
  );
end entity sign_extension;

architecture arch of sign_extension is
signal sign_bit : std_logic;
begin
  sign_bit <= immediate(21);
  sign_extended_immediate <=  (sign_bit & sign_bit & sign_bit & sign_bit & sign_bit & sign_bit & sign_bit & sign_bit &immediate) & "00";
end arch;



