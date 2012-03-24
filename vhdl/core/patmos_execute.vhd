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
    inst_type                       : in instruction_type;
    ALU_function_type               : in unsigned(3 downto 0);
    ALU_instruction_type            : in ALU_inst_type;
    ALUi_immediate                  : in unsigned(11 downto 0);
    rs                              : in unsigned(31 downto 0);
    rt                              : in unsigned(31 downto 0);
    pd                              : out unsigned(2 downto 0);  -- this is the index of predicate bit, ALUp instructions write in predicate bits and others use them!
    rd                              : out unsigned(31 downto 0);
    wb_we                           : in std_logic;
    wb_we_exec                      : out std_logic;
    fw_alu                          : in unsigned(31 downto 0);
    fw_mem                          : in unsigned(31 downto 0);
    fw_ctrl_rs                      : in forwarding_type;
    fw_ctrl_rt                      : in forwarding_type;
    ld_type                         : in load_type;
    load_immediate                  : in unsigned(6 downto 0);
    ld_function_type                : in unsigned(1 downto 0);
    load_store_address              : out unsigned(31 downto 0);
    data_mem_read                   : out std_logic;
    rs1_in                          : in unsigned(4 downto 0); 
    rs1_out                         : out unsigned(4 downto 0);
    rs2_in                          : in unsigned(4 downto 0); 
    rs2_out                         : out unsigned(4 downto 0) 
  );

end entity patmos_execute;

architecture arch of patmos_execute is

function shift_right_arith (rs, rt : unsigned(31 downto 0))
                 return unsigned is
  variable shift_out  : unsigned(31 downto 0):= (others => '0');
  variable shift_value  : unsigned(4 downto 0):= (others => '0');
begin
  shift_value(4 downto 0 ) := rt(4 downto 0);
  case (shift_value) is
      when "00000" => shift_out := rs;
      when "00001" => shift_out :=  rs(31) & rs(31 downto 1); 
      when "00010" => shift_out :=  rs(31 downto 30) & rs(31 downto 2);   
      when "00011" => shift_out :=  rs(31 downto 29) & rs(31 downto 3); 
      when "00100" => shift_out :=  rs(31 downto 28) & rs(31 downto 4); 
      when "00101" => shift_out :=  rs(31 downto 27) & rs(31 downto 5); 
      when "00110" => shift_out :=  rs(31 downto 26) & rs(31 downto 6); 
      when "00111" => shift_out :=  rs(31 downto 25) & rs(31 downto 7); 
      when "01000" => shift_out :=  rs(31 downto 24) & rs(31 downto 8); 
      when "01001" => shift_out :=  rs(31 downto 23) & rs(31 downto 9) ; 
      when "01010" => shift_out :=  rs(31 downto 22) & rs(31 downto 10); 
      when "01011" => shift_out :=  rs(31 downto 21) & rs(31 downto 11); 
      when "01100" => shift_out :=  rs(31 downto 20) & rs(31 downto 12); 
      when "01101" => shift_out :=  rs(31 downto 19) & rs(31 downto 13); 
      when "01110" => shift_out :=  rs(31 downto 18) & rs(31 downto 14); 
      when "01111" => shift_out :=  rs(31 downto 17) & rs(31 downto 15); 
      when "10000" => shift_out :=  rs(31 downto 16) & rs(31 downto 16); 
      when "10001" => shift_out :=  rs(31 downto 15) & rs(31 downto 17); 
      when "10010" => shift_out :=  rs(31 downto 14) & rs(31 downto 18);   
      when "10011" => shift_out :=  rs(31 downto 13) & rs(31 downto 19); 
      when "10100" => shift_out :=  rs(31 downto 12) & rs(31 downto 20); 
      when "10101" => shift_out :=  rs(31 downto 11) & rs(31 downto 21); 
      when "10110" => shift_out :=  rs(31 downto 10) & rs(31 downto 22); 
      when "10111" => shift_out :=  rs(31 downto 9) & rs(31 downto 23); 
      when "11000" => shift_out :=  rs(31 downto 8) & rs(31 downto 24); 
      when "11001" => shift_out :=  rs(31 downto 7) & rs(31 downto 25); 
      when "11010" => shift_out :=  rs(31 downto 6) & rs(31 downto 26); 
      when "11011" => shift_out :=  rs(31 downto 5) & rs(31 downto 27); 
      when "11100" => shift_out :=  rs(31 downto 4) & rs(31 downto 28); 
      when "11101" => shift_out :=  rs(31 downto 3) & rs(31 downto 29); 
      when "11110" => shift_out :=  rs(31 downto 2) & rs(31 downto 30); 
      when "11111" => shift_out :=  rs(31 downto 1) & rs(31 downto 31); 
      when  others => shift_out := rs;
   end case;
  return shift_out ;
end shift_right_arith;

signal fw_out_rs            : unsigned(31 downto 0);
signal fw_out_rt            : unsigned(31 downto 0);   

begin

  fw_a: entity work.forward_value_select(arch)
	port map(fw_alu, fw_mem, rs, fw_out_rs, fw_ctrl_rs);
  
  fw_b: entity work.forward_value_select(arch)
	port map(fw_alu, fw_mem, rt, fw_out_rt, fw_ctrl_rt);
  
  alu_op: process(clk)
  begin
    if rising_edge(clk) then
      
  --if (inst_type = ALU)--if ALU
  case inst_type is
    when ALU => 
      wb_we_exec <= wb_we;
      rs1_out <= rs1_in;
      rs2_out <= rs2_in;
      case ALU_instruction_type is 
        when ALUr => 
         case ALU_function_type is
          when "0000" => rd <= fw_out_rs + fw_out_rt;
          when "0001" => rd <= rs - rt;
          when "0010" => rd <= rt - rs;
          when "0011" => rd <= SHIFT_LEFT(rs, to_integer(rt));
          when "0100" => rd <= SHIFT_RIGHT(rs, to_integer(rt));
          ------------------?????when "0101" => rd <= SHIFT_RIGHT(signed(rs), to_integer(rt));
          when "0110" => rd <= rs or rt ;
          when "0111" => rd <= rs and rt ;
        --??  when "1000" => rd <= shift_left_logical(rs, rt) or ; --??
          when "1001" => rd <= rs - rt; --??
          when "1010" => rd <= rt xor rs; 
          when "1011" => rd <= rs nor rt; 
       --   when "1100" => rd <= shift_right_logical(rs, rt); --??
       --   when "1101" => rd <= shift_right_arith(rs, rt); --??
          when "1110" => rd <= SHIFT_LEFT(rs, 1)+ rt;
          when "1111" => rd <= SHIFT_LEFT(rs, 2) + rt ;  
          when others => NULL;
       end case;
       
      when ALUu =>    
        wb_we_exec <= wb_we;
        rs1_out <= rs1_in;
        rs2_out <= rs2_in;
        case ALU_function_type is
          when "0000" => rd <= rs(7)& rs(7) &rs(7)& rs(7)& rs(7)& rs(7)& rs(7)& rs(7)& 
                               rs(7)& rs(7) &rs(7)& rs(7)& rs(7)& rs(7)& rs(7)& rs(7)&
                               rs(7)& rs(7) &rs(7)& rs(7)& rs(7)& rs(7)& rs(7)& rs(7)& 
                               rs(7 downto 0);
          when "0001" => rd <= rs(7)& rs(7) &rs(7)& rs(7)& rs(7)& rs(7)& rs(7)& rs(7)&
                               rs(7)& rs(7) &rs(7)& rs(7)& rs(7)& rs(7)& rs(7)& rs(7)& 
                               rs(15 downto 0);
          when "0010" => rd <= "0000000000000000" & rs(15 downto 0);
          when "0101" => rd <= "0" & rs(30 downto 0);
          when others => NULL;
        end case;   
      
      --
      --when ALUm =>
    --  case ALU_function_type is
      --    when "0000" => rd <= mul rs rt;
        --  when "0001" => rd <= ;
        --  when others => NULL;
      --end case;
      when ALUc =>    
        wb_we_exec <= wb_we;
        rs1_out <= rs1_in;
        rs2_out <= rs2_in;
        case ALU_function_type is
          when "0000" => 
            if (rs = "00000000000000000000000000000000") then
              pd <= "001"; -- predicate_reg(pd)
            else
              pd <= "000";
            end if;
          when "0001" => 
            if (rs /=  "00000000000000000000000000000000")then
              pd <= "001";
            else
              pd <= "000";
            end if;
          when "0010" => 
            if (rs = rt)then
              pd <= "001";
            else
              pd <= "000";
            end if;
          when "0011" =>
            if (rs /= rt)then
              pd <= "001";
            else
              pd <= "000";
            end if;
          when "0100" =>
            if (rs < rt)then
              pd <= "001";
            else
              pd <= "000";
            end if;
          when "0101" => 
            if (rs <= rt)then
              pd <= "001";
            else
              pd <= "000";
            end if;
          when "0110" => 
            if (unsigned(rs) < unsigned(rt))then
              pd <= "001";
            else
              pd <= "000";
            end if;
          when "0111" =>
            if (unsigned(rs) <= unsigned(rt))then
              pd <= "001";
            else
              pd <= "000";
            end if;
        --  when "1000" =>
        --    if ((rs and SHIFT_LEFT(("00000000000000000000000000000001", to_integer(rt)))) == "11111111111111111111111111111111") then
        --      pd <= "001";
          --  else
          --    pd <= "000";
          --  end if;
          when others => NULL;
        end case; 
      --  
    when others => NULL; -- case ALU_inst_type (ALUr, ALUc, ...)
    end case; 
    when ALUi =>
      data_mem_read <= '0';
      wb_we_exec <= wb_we;
      rs1_out <= rs1_in;
      rs2_out <= rs2_in;
        case ALU_function_type is
          when "0000" => rd <= fw_out_rs + ("00000000000000000000" & ALUi_immediate);
          when "0001" => rd <= fw_out_rs - ("00000000000000000000" & ALUi_immediate);
          when "0010" => rd <= ("00000000000000000000" & ALUi_immediate) - fw_out_rs;
          when "0011" => rd <= SHIFT_LEFT(fw_out_rs, to_integer(("00000000000000000000" & ALUi_immediate)));
          when "0100" => rd <= SHIFT_RIGHT(fw_out_rs, to_integer(("00000000000000000000" & ALUi_immediate)));
          when "0101" => rd <= shift_right_arith(fw_out_rs, ("00000000000000000000" & ALUi_immediate));
          when "0110" => rd <= fw_out_rs or ("00000000000000000000" & ALUi_immediate);
          when "0111" => rd <= fw_out_rs and ("00000000000000000000" & ALUi_immediate);
          when others => rd <= fw_out_rs + ("00000000000000000000" & ALUi_immediate);
        end case;
  when LDT => 
        wb_we_exec <= wb_we; 
        data_mem_read <= '1';
        case ld_type is 
          when lw => 
            load_store_address <= fw_out_rs + ("0000000000000000000000000" & load_immediate(4 downto 0));
       --   when lh =>
       --   when lb =>
       --   when lhu =>
       --   when lbu =>
       --   when dlwh =>
       --   when dlbh =>
       --   when dlbu =>
          when others => null;  
        end case;
   --when STT =>
      
  when others => NULL; -- inst_type
  end case;
end if;
  end process alu_op;
end arch;


 
 -------------------------------------
 -- multiply
 -------------------------------------

