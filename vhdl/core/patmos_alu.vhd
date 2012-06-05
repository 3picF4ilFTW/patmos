library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;
use work.patmos_type_package.all;

entity patmos_alu is
    port
    (
        clk                         : in std_logic;
        rst                         : in std_logic;
        din                         : in alu_in_type;
        dout                        : out alu_out_type
    );
end entity patmos_alu;

architecture arch of patmos_alu is
--	COMPONENT megaddsub
--	PORT (
--			add_sub							: IN STD_LOGIC ;
--			dataa, datab 					: IN STD_LOGIC_VECTOR(31 DOWNTO 0) ;
--			result							: OUT STD_LOGIC_VECTOR(31 DOWNTO 0) 
--			) ;
--	END COMPONENT ;
	
	--signal intermediate_add : STD_LOGIC_VECTOR(31 DOWNTO 0);
	--signal intermediate_sub : STD_LOGIC_VECTOR(31 DOWNTO 0);
	signal number_of_bytes_in_stack_cache			: unsigned(4 downto 0) := (others => '0');
	
begin
	--add: megaddsub
	--PORT MAP ('1', STD_LOGIC_VECTOR(din.rs1), STD_LOGIC_VECTOR(din.rs2), intermediate_add ) ;
	--sub: megaddsub
	--PORT MAP ('0', STD_LOGIC_VECTOR(din.rs1), STD_LOGIC_VECTOR(din.rs2), intermediate_sub ) ;
		
    patmos_alu: process(din)
    begin
      case din.inst_type is
       when ALUi =>
        case din.ALU_function_type is
          when "0000" => dout.rd <= din.rs1 + din.rs2; -- unsigned(intermediate_add);
          when "0001" => dout.rd <= din.rs1 - din.rs2; --unsigned(intermediate_sub);--
--          when "0010" => dout.rd <= din.rs2 - din.rs1;
          when "0011" => dout.rd <= SHIFT_LEFT(din.rs1, to_integer(din.rs2));
          when "0100" => dout.rd <= SHIFT_RIGHT(din.rs1, to_integer(din.rs2));
        --  when "0101" => dout.rd <= shift_right_arith(din.rs, ("00000000000000000000" & din.ALUi_immediate));
         when "0110" => dout.rd <= din.rs1 or din.rs2;
          when "0111" => dout.rd <= din.rs1 and din.rs2;
          when others => dout.rd <= din.rs1 + din.rs2; --unsigned(intermediate_add); 
        end case; 
      when ALU =>
        case din.ALU_instruction_type is
          when ALUr => 
            case din.ALU_function_type is
              when "0000" => dout.rd <= din.rs1 + din.rs2; --unsigned(intermediate_add);--
              when "0001" => dout.rd <= din.rs1 - din.rs2; --unsigned(intermediate_sub);--
              when "0010" => dout.rd <= din.rs2 - din.rs1; --unsigned(intermediate_sub);--
              when "0011" => dout.rd <= SHIFT_LEFT(din.rs1, to_integer(din.rs2));
              when "0100" => dout.rd <= SHIFT_RIGHT(din.rs1, to_integer(din.rs2));
          ------------------?????when "0101" => rd <= SHIFT_RIGHT(signed(rs), to_integer(rt));
              when "0110" => dout.rd <= din.rs1 or din.rs2 ;
              when "0111" => dout.rd <= din.rs1 and din.rs2 ;
              --??  when "1000" => rd <= shift_left_logical(rs, rt) or ; --??
              when "1001" => dout.rd <= din.rs1 - din.rs2; --unsigned(intermediate_sub);-- --??
              when "1010" => dout.rd <= din.rs2 xor din.rs1; 
              when "1011" => dout.rd <= din.rs1 nor din.rs2; 
              --   when "1100" => rd <= shift_right_logical(rs, rt); --??
              --   when "1101" => rd <= shift_right_arith(rs, rt); --??
              when "1110" => dout.rd <= SHIFT_LEFT(din.rs1, 1)+ din.rs2;
              when "1111" => dout.rd <= SHIFT_LEFT(din.rs1, 2) + din.rs2 ;  
              when others => dout.rd <= din.rs1 + din.rs2; --unsigned(intermediate_add); --
            end case;
          when ALUu =>
            case din.ALU_function_type is
              when "0000" => dout.rd <= din.rs1(7)& din.rs1(7) & din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& 
                               din.rs1(7)& din.rs1(7) &din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)&
                               din.rs1(7)& din.rs1(7) &din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& 
                               din.rs1(7 downto 0);
              when "0001" => dout.rd <= din.rs1(7)& din.rs1(7) &din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)&
                               din.rs1(7)& din.rs1(7) &din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& din.rs1(7)& 
                               din.rs1(15 downto 0);
              when "0010" => dout.rd <= "0000000000000000" & din.rs1(15 downto 0);
              when "0101" => dout.rd <= "0" & din.rs1(30 downto 0);
              when others => dout.rd <= din.rs1 + din.rs2;
            end case;
          when others => dout.rd <= din.rs1 + din.rs2;
        end case;
        when LDT =>
            dout.rd <= din.rs1 + din.rs2; --unsigned(intermediate_add);--
        when STT =>
            dout.rd <= din.rs1 + din.rs2; -- unsigned(intermediate_add);--
        when BEQ =>
            dout.rd <= din.rs1 + din.rs2; -- unsigned(intermediate_add);--
        when STC => 
        	case din.STC_instruction_type is
        		when SRES => --reserve
        			--dout.rd <= din.stack_data_in;
        				
        			if (number_of_bytes_in_stack_cache = 0 and din.head_in = din.tail_in) then--stack empty
        				    dout.spill_out <= '0';
        					dout.tail_out <= din.tail_in;
        					dout.head_out <= (din.head_in + din.stc_immediate_in) mod 32;
        					--if ((number_of_bytes_in_stack_cache + din.stc_immediate_in) > 32) then
        					--	number_of_bytes_in_stack_cache <= 32;
        					--else
        						number_of_bytes_in_stack_cache <= number_of_bytes_in_stack_cache + din.stc_immediate_in;
        		--	elsif (din.head_in = din.tail_in) then	-- stack full (covered under the next if)
        		--			dout.spill_out <= '1';
        						
        			elsif ((32 - number_of_bytes_in_stack_cache) <  din.stc_immediate_in) then -- needs to spill
        				--if( ( 32 - (din.head_in - din.tail_in)) < din.stc_immediate_in) then
        					number_of_bytes_in_stack_cache <= 32;
        					dout.spill_out <= '1'; -- how much to spill? next line
        					dout.rd <=  "000000000000000000000000000" & din.stc_immediate_in - (32 - number_of_bytes_in_stack_cache); 
        					dout.tail_out <= (din.tail_in + din.stc_immediate_in - (32 - number_of_bytes_in_stack_cache)) mod 32;
        					dout.head_out <= (din.head_in + din.stc_immediate_in) mod 32;
        					dout.st_out <= din.st_in + ("000000000000000000000000000" & din.stc_immediate_in);
        				else 
        					number_of_bytes_in_stack_cache <= number_of_bytes_in_stack_cache + din.stc_immediate_in;
        					dout.spill_out <= '0';
        					dout.tail_out <= din.tail_in;
        				end if;	
        			--elsif (din.head_in < din.tail_in) then
        			--	if( ( 32 - (din.tail_in - din.head_in )) < din.stc_immediate_in) then
        			--		dout.spill_out <= '1'; -- how much to spill?
        			--		dout.rd <=  "000000000000000000000000000" & din.stc_immediate_in - (32 - (din.tail_in - din.head_in )); 
        			--		dout.tail_out <= din.head_in + din.stc_immediate_in - (32 - (din.tail_in - din.head_in)); -- mod . . .
        			--		dout.head_out <= (din.head_in + din.stc_immediate_in) mod 32;
        			--	else 
        			--		dout.spill_out <= '0';
        			--		dout.tail_out <= din.tail_in;
        			--	end if;	
        			--end if;	
        				
        			--dout.head_out <= din.head_in + din.stc_immediate_in; 
        		--when "01" => --ensure
        		--when "10" => --free
        		when others => NULL;
        	
    	  end case; --inst type
    	when others => dout.rd <= din.rs1 + din.rs2; -- unsigned(intermediate_add);--
    	end case;
    end process patmos_alu;
end arch;


