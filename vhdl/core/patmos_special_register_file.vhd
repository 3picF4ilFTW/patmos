------------------------------------------
--special purpose registers
------------------------------------------

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;


entity patmos_special_register_file is --general purpose registers
  port
  (
    clk           : in std_logic;
    rst           : in std_logic;
    read_address1 : in unsigned(3 downto 0);
    read_address2 : in unsigned(3 downto 0);
    write_address : in unsigned(3 downto 0);
    read_data1    : out unsigned(31 downto 0);
    read_data2    : out unsigned(31 downto 0);
    write_data    : in unsigned(31 downto 0);
    write_enable  : in std_logic
  );
end entity patmos_special_register_file;

architecture arch of patmos_special_register_file is
type special_register_bank is array (0 to 15) of unsigned(31 downto 0);
signal special_reg_bank : special_register_bank;
signal reg_read_address1, reg_read_address2 : unsigned(4 downto 0);
begin
  --                                  
  ------ latch read address
  latch_read_address:  process (clk, rst)
  begin
    if(rst = '1') then
       -- for i in 0 to 15 loop -- initialize register file
          special_reg_bank(7)<= "00000000000000000000000011001000";
       -- end loop;
    elsif rising_edge(clk) then
   --   if (read_enable) then
      --    reg_read_address1 <= read_address1;
      --    reg_read_address2 <= read_address2;
          if (write_enable = '1') then
             special_reg_bank(to_integer(unsigned(write_address))) <= write_data;
           end if;
   --   end if;
    end if;
   end process latch_read_address;
   
 ------ read process (or should be async?)
  read:  process (read_address1, read_address2)
  begin
   -- if ((read_address1 = write_address) and write_enable = '1' )then
  --   read_data1 <= write_data;
  -- else 
      read_data1 <= special_reg_bank(to_integer(unsigned(read_address1)));
 --   end if;
    
  -- if (read_address2 = write_address) and write_enable = '1' then
    --  read_data2 <= write_data;
  -- else   
      read_data2 <= special_reg_bank(to_integer(unsigned(read_address2)));
  --  end if;
  end process read;
  
end arch;


