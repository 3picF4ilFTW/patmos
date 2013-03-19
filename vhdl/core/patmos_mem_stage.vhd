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
--------------------------------------------------------------------------------


library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;
use work.patmos_type_package.all;
use work.sc_pack.all;



entity patmos_mem_stage is
    port(
        clk                : in  std_logic;
        rst                : in  std_logic;
        mem_write          : in  std_logic;
        mem_data_out_muxed : in  std_logic_vector(31 downto 0);
        exout_reg          : in  execution_reg;
        exout_not_reg      : in  execution_not_reg;
        dout               : out mem_out_type;
        decdout            : in  decode_out_type
        -- SDRAM controller interface

    );
end entity patmos_mem_stage;

use work.patmos_config.all;
architecture arch of patmos_mem_stage is
    
	signal cnt      						 : unsigned(1 downto 0);
	signal gm_rd							 : std_logic;
	signal ld_data_mem						 : std_logic_vector(31 downto 0);
	signal ld_data_word						 : std_logic_vector(31 downto 0);
	signal ld_data_half						 : std_logic_vector(15 downto 0);
	signal ld_data_byte						 : std_logic_vector(7 downto 0);
	signal ld_half_ext						 : std_logic_vector(31 downto 0);
	signal ld_byte_ext						 : std_logic_vector(31 downto 0);
	signal ld_data_out						 : std_logic_vector(31 downto 0);
	signal test								 : std_logic_vector(31 downto 0);

    signal en : std_logic_vector(3 downto 0);

    signal lm_dout        : std_logic_vector(31 downto 0);
    signal mem_write_data : std_logic_vector(31 downto 0);

    signal byte_enable                       : std_logic_vector(3 downto 0);
    signal word_enable                       : std_logic_vector(1 downto 0);
    signal ldt_type                          : address_type;
    signal datain                            : std_logic_vector(31 downto 0);

    signal s_u                               : std_logic;
    signal exout_reg_adr, prev_exout_reg_adr : std_logic_vector(31 downto 0);
    signal exout_reg_adr_shft                : std_logic_vector(31 downto 0);
    signal mem_write_data_stall              : std_logic_vector(31 downto 0);
    signal prev_mem_write_data_reg           : std_logic_vector(31 downto 0);
    signal prev_en_reg                       : std_logic_vector(3 downto 0);
    signal en_reg                            : std_logic_vector(3 downto 0);
    -- Data Cache
    signal dc_data_out                       : std_logic_vector(31 downto 0);

    -- Main Memory
    signal gm_write_data             : std_logic_vector(31 downto 0);
    signal gm_write_data_reg         : std_logic_vector(31 downto 0);
    signal gm_data_out               : std_logic_vector(31 downto 0);
    signal gm_en_reg                 : std_logic_vector(3 downto 0);
    signal gm_read_add, gm_write_add : std_logic_vector(9 downto 0);
    signal gm_write_add_reg			  : std_logic_vector(9 downto 0);           
    signal gm_en_spill               : std_logic_vector(3 downto 0);
    signal gm_en_spill_reg           : std_logic_vector(3 downto 0);
    signal gm_spill                  : std_logic_vector(3 downto 0);
    signal gm_byte_enable            : std_logic_vector(3 downto 0);
    signal gm_word_enable            : std_logic_vector(1 downto 0);
    signal gm_read_data              : std_logic_vector(31 downto 0);
    signal gm_en                     : std_logic_vector(3 downto 0);
    signal prev_gm_en_reg            : std_logic_vector(3 downto 0);
    signal gm_read_done, gm_write_done : std_logic;
    signal gm_do_read, gm_do_write : std_logic;

    ------ stack cache
    signal sc_en                    : std_logic_vector(3 downto 0);
    signal sc_word_enable           : std_logic_vector(1 downto 0);
    signal sc_byte_enable           : std_logic_vector(3 downto 0);
    signal sc_read_data             : std_logic_vector(31 downto 0);
    
    
    signal ld_data, prev_ld_data    : std_logic_vector(31 downto 0);

    signal state_reg, next_state 	: sc_state;
    signal mem_top, mem_top_next	 : std_logic_vector(31 downto 0);
    signal sc_fill               	: std_logic_vector(3 downto 0);

    signal spill, fill                   : std_logic;
    signal stall, sc_need_stall          : std_logic;
    signal spill_fill_reg				  : std_logic;
    signal nspill_fill, nspill_fill_next : std_logic_vector(31 downto 0);

    signal cpu_out : cpu_out_type;
    signal cpu_in  : cpu_in_type;

    signal gm_out : gm_out_type;
    signal gm_in  : gm_in_type;
	signal gm_write_res					: std_logic_vector(31 downto 0);

	signal prev_mem_data_out_muxed  	:  std_logic_vector(31 downto 0);

begin

	test <= ld_data_out;


    mem_wb : process(clk)
    begin
        if (rising_edge(clk)) then
            dout.data_out           <= datain;
            -- forwarding
            dout.reg_write_out      <= exout_reg.reg_write or exout_reg.mem_to_reg;
            dout.write_back_reg_out <= exout_reg.write_back_reg;
            ldt_type                <= decdout.adrs_type;
            s_u                     <= decdout.s_u;
        end if;
    end process mem_wb;

    --- main memory for simulation
    -- Ms: as you exchange 32-bit words you can have one memory with 32 bits
    -- instead of four byte memories.

	gm_write_res <= std_logic_vector(signed(mem_top) - 1);
    process(exout_reg_adr_shft, spill, fill, mem_top, gm_data_out, mem_write_data_stall, gm_en_reg
    	, gm_write_add_reg, gm_en_spill_reg, gm_in, spill_fill_reg
    ) --SA: Main memory read/write address, normal load/store or fill/spill
    begin
        gm_read_add   <= exout_reg_adr_shft(9 downto 0);
        gm_write_add  <= exout_reg_adr_shft(9 downto 0);
        gm_en_spill   <= gm_en_reg;
        gm_write_data <= mem_write_data_stall;
        --		gm_read_data		<= gm_data_out;
        if (spill_fill_reg = '1') then --if (spill = '1' or fill = '1') then
            
            gm_write_add   <= gm_write_add_reg(9 downto 0);
            gm_en_spill   <= gm_en_spill_reg;  -- this is for spilling ( writing to global memory)
            gm_write_data <= gm_in.wr_data; -- comes from sc

        end if;
        if(spill = '1' or fill = '1') then
        	gm_read_add   <= mem_top(9 downto 0);
        end if;
    end process;
    
    process(clk) -- compensate one clock delay of loading from stack cache
    begin
    	if (rising_edge (clk)) then
    		gm_write_add_reg   <= gm_write_res(9 downto 0);
            gm_en_spill_reg   <= gm_spill;  -- this is for spilling ( writing to global memory)
            spill_fill_reg	  <= spill or fill;
    	end if;
    end process;

        gm0 : entity work.patmos_data_memory(arch)
            generic map(8, 10)
            port map(clk,
                     gm_write_add,
                     gm_write_data(7 downto 0),
                     gm_en_spill(0),
                     gm_read_add,
                     gm_read_data(7 downto 0));

        gm1 : entity work.patmos_data_memory(arch)
            generic map(8, 10)
            port map(clk,
                     gm_write_add,
                     gm_write_data(15 downto 8),
                     gm_en_spill(1),
                     gm_read_add,
                     gm_read_data(15 downto 8));

        gm2 : entity work.patmos_data_memory(arch)
            generic map(8, 10)
            port map(clk,
                     gm_write_add,
                     gm_write_data(23 downto 16),
                     gm_en_spill(2),
                     gm_read_add,
                     gm_read_data(23 downto 16));

        gm3 : entity work.patmos_data_memory(arch)
            generic map(8, 10)
            port map(clk,
                     gm_write_add,
                     gm_write_data(31 downto 24),
                     gm_en_spill(3),
                     gm_read_add,
                     gm_read_data(31 downto 24));
--    end generate GM_block_ram;

    ---------------------------------------------- stack cache
    --	        clk       	             : in std_logic;
    --        wr_address               : in std_logic_vector(addr_width -1 downto 0);
    --        data_in                  : in std_logic_vector(width -1 downto 0); -- store
    --        write_enable             : in std_logic;
    --        rd_address               : in std_logic_vector(addr_width - 1 downto 0);
    --        data_out                 : out std_logic_vector(width -1 downto 0) -- load

    stack_cache : entity work.patmos_stack_cache(arch)
        port map(
            clk,
            rst,
            cpu_out,
            cpu_in,
            gm_out,
            gm_in
        );

    process(exout_reg_adr, sc_en, gm_read_data, cpu_in, spill, fill, gm_write_res, mem_top, sc_fill, mem_write_data_stall)
    begin
        cpu_out.address    <= exout_reg_adr;
        cpu_out.sc_en      <= sc_en;
        gm_out.wr_data     <= gm_read_data;
        sc_read_data       <= cpu_in.rd_data;
        cpu_out.spill_fill <= spill or fill;
        cpu_out.mem_top    <= gm_write_res;
        cpu_out.sc_fill    <= sc_fill;
        cpu_out.wr_add     <= gm_write_res(sc_length - 1 downto 0) and SC_MASK;
        cpu_out.wr_data    <= mem_write_data_stall;
    end process;
    --sc[mem_top & SC_MASK] = mem[mem_top];


    process(clk, rst)
    begin
        if rst = '1' then
            state_reg <= init;
			gm_rd	  <= '0';
        	
        elsif rising_edge(clk) then	
            state_reg   	   <= next_state;
            mem_top            <= mem_top_next;
            nspill_fill        <= nspill_fill_next;
            if cnt = "11"  then
                    cnt <= (others => '0');
                    gm_rd <= '1';
                else
                    cnt <= cnt + 1;
                end if;
        end if;
    end process;

	process( gm_spill, exout_not_reg ) -- SA: This is a temporary fix, need to find when they actually should change 
	begin
		gm_do_write  <=  '0';
        gm_do_read   <= '0';
        gm_read_done <= '1';
        gm_write_done <= '1';
        if (gm_spill(0) = '1' or exout_not_reg.gm_read_not_reg = '1') then
 --       	gm_write_done       <= gm_slave.SDataAccept;
 --       	gm_read_done       <= gm_slave.SResp;
        	gm_do_write        <= gm_spill(0) or gm_spill(1) or gm_spill(2) or gm_spill(3); 
        	gm_do_read         <= exout_not_reg.gm_read_not_reg;
        end if;
	end process;
    process(state_reg, exout_not_reg, spill, fill) -- adjust tail
    begin
        next_state <= state_reg;
        case state_reg is
            when init =>
                if (exout_not_reg.spill = '1') then
                    next_state <= spill_state;
                elsif (exout_not_reg.fill = '1') then
                    next_state <= fill_state;
                elsif (exout_not_reg.free = '1') then
                    next_state <= free_state;
                else
                    next_state <= init;
                end if;
            when spill_state =>
                if (spill = '1') then
                    next_state <= spill_state;
                else
                    next_state <= init;
                end if;
            when fill_state =>
                if (fill = '1') then
                    next_state <= fill_state;
                else
                    next_state <= init;
                end if;
            when free_state =>
                next_state <= init;
        end case;
    end process;

    -- Output process
    process(state_reg, exout_not_reg, mem_top, nspill_fill, decdout)
    begin
        if (decdout.spc_reg_write(6) = '1') then
            mem_top_next <= exout_not_reg.mem_top;
        else
            mem_top_next <= mem_top;
        end if;
        --	dout.stall 			<= '0';
        sc_need_stall            <= '0';
        spill            <= '0';
        fill             <= '0';
        gm_spill         <= "0000";
        sc_fill          <= "0000";
        nspill_fill_next <= exout_not_reg.nspill_fill;
        case state_reg is
            when init =>
                sc_fill          <= "0000";
                gm_spill         <= "0000";
                nspill_fill_next <= exout_not_reg.nspill_fill;
            --		dout.stall <= '0';
            when spill_state =>
                if ((signed(nspill_fill) - 1) >= 0) then 
                    mem_top_next     <= std_logic_vector(signed(mem_top) - 1);
                    nspill_fill_next <= std_logic_vector(signed(nspill_fill) - 1);
                    gm_spill         <= "1111"; -- spill in words?
                    spill            <= '1';
                    sc_need_stall            <= '1';
                else
                    gm_spill         <= "0000";
                    spill            <= '0';
                    sc_need_stall            <= '0';
                    nspill_fill_next <= exout_not_reg.nspill_fill;
                end if;

            when fill_state =>
                if ((signed(nspill_fill) - 1) >= 0) then
                    mem_top_next     <= std_logic_vector(signed(mem_top) + 1);
                    nspill_fill_next <= std_logic_vector(signed(nspill_fill) - 1);
                    sc_fill          <= "1111"; -- fill in words?
                    fill             <= '1';
                    sc_need_stall            <= '1';
                else
                    sc_fill          <= "0000";
                    fill             <= '0';
                    sc_need_stall            <= '0';
                    nspill_fill_next <= exout_not_reg.nspill_fill;
                end if;
            when free_state =>
                if (exout_not_reg.sc_top > mem_top) then
                    mem_top_next <= exout_not_reg.sc_top;
                end if;
        end case;
    end process;

    -- Edgar: the process here is not needed (unless you want it to create a label for assignments)
    --process(mem_top, stall)
    --begin
        dout.mem_top <= mem_top;
        dout.stall   <= stall;
        -- Edgar: sc_need_stall might be artificial in the future, but it's easier to think this way for now
        stall <= sc_need_stall;-- or (gm_do_read and not gm_read_done) or (gm_do_write and not gm_write_done);
        cpu_out.fill <= fill;
        cpu_out.spill <= spill;
    --end process;
    -----------------------------------------------
    -- MS: If a registered address from EX is used here and there is an address
    -- register in the memory, are we now moving the MEM stage into WB?
    -- SA: The address is not registered, in case there is the stall the address
    -- should be registered, I can change the name though
    -- MS: a non-registered signal shall not end with _reg.
    -- SA: Changed the name to _stall
    lm0 : entity work.patmos_data_memory(arch)
        generic map(8, 10)
        port map(clk,
                 exout_reg_adr_shft(9 downto 0), -- exout_not_reg.adrs(9 downto 0),
                 mem_write_data_stall(7 downto 0), --mem_write_data0,
                 en_reg(0),
                 exout_reg_adr_shft(9 downto 0), --exout_not_reg.adrs(9 downto 0),
                 lm_dout(7 downto 0));

    lm1 : entity work.patmos_data_memory(arch)
        generic map(8, 10)
        port map(clk,
                 exout_reg_adr_shft(9 downto 0), --exout_not_reg.adrs(9 downto 0),
                 mem_write_data_stall(15 downto 8), --mem_write_data1,
                 en_reg(1),
                 exout_reg_adr_shft(9 downto 0), --exout_not_reg.adrs(9 downto 0),
                 lm_dout(15 downto 8));

    lm2 : entity work.patmos_data_memory(arch)
        generic map(8, 10)
        port map(clk,
                 exout_reg_adr_shft(9 downto 0), --exout_not_reg.adrs(9 downto 0),
                 mem_write_data_stall(23 downto 16), --mem_write_data2,
                 en_reg(2),
                 exout_reg_adr_shft(9 downto 0), --exout_not_reg.adrs(9 downto 0),
                 lm_dout(23 downto 16));

    lm3 : entity work.patmos_data_memory(arch)
        generic map(8, 10)
        port map(clk,
                 exout_reg_adr_shft(9 downto 0), --exout_not_reg.adrs(9 downto 0),
                 mem_write_data_stall(31 downto 24), --
                 en_reg(3),
                 exout_reg_adr_shft(9 downto 0), --exout_not_reg.adrs(9 downto 0),
                 lm_dout(31 downto 24));

    process(clk)                        --to register the enable and address and data of memory in case of stall
    begin
        --	if (rst = '1') then
        --			exout_reg_adr		<= exout_not_reg.adrs;
        --			mem_write_data0_stall <= mem_write_data0;
        --			mem_write_data1_stall <= mem_write_data1;
        --			mem_write_data2_stall <= mem_write_data2;
        --			mem_write_data3_stall <= mem_write_data3;
        if rising_edge(clk) then
            prev_exout_reg_adr      <= exout_not_reg.adrs;
            prev_mem_write_data_reg <= mem_write_data;
            prev_en_reg             <= en;
			
            prev_ld_data 			<= ld_data;
            prev_gm_en_reg			<= gm_en_reg;
			prev_mem_data_out_muxed	<= mem_data_out_muxed;
        end if;
    end process;

    process(stall, en, gm_en, prev_en_reg, exout_not_reg, mem_write_data, prev_exout_reg_adr, prev_mem_write_data_reg)
    begin        
       
        exout_reg_adr        <= exout_not_reg.adrs;
        mem_write_data_stall <= mem_write_data;
        gm_en_reg            <= gm_en;
        en_reg               <= en;
        
        if (stall = '1') then
            exout_reg_adr        <= prev_exout_reg_adr;
            mem_write_data_stall <= prev_mem_write_data_reg;
            en_reg               <= prev_en_reg;
            gm_en_reg            <= prev_gm_en_reg;
        end if;
    end process;

    process(exout_reg_adr)
    begin
        exout_reg_adr_shft <= "00" & exout_reg_adr(31 downto 2);
    end process;
    ------------------------- ld from stack cache or  io/scratchpad or main memory? -----------------------------

    process(exout_reg, mem_data_out_muxed
    	, lm_dout, sc_read_data, gm_read_data
    )
    begin
        ld_data_mem	<= gm_read_data;
        if (exout_reg.lm_read = '1') then        
            ld_data_mem <= lm_dout; 
            
        elsif (exout_reg.sc_read = '1') then           
            ld_data_mem <= sc_read_data;
        elsif (exout_reg.gm_read = '1') then
			ld_data_mem <= gm_read_data;
        elsif (exout_reg.dc_read = '1') then                
            ld_data_mem <= lm_dout;
        end if;
    end process;


    --------------------------- address muxes begin--------------------------		     
    process(lm_dout, exout_reg, sc_read_data, gm_read_data, ld_data_mem)
    begin
		ld_data_word <= ld_data_mem(7 downto 0) & ld_data_mem(15 downto 8) & ld_data_mem(23 downto 16) & ld_data_mem(31 downto 24);
        case exout_reg.adrs_reg(1) is
            when '0' =>  
                ld_data_half <= ld_data_mem(7 downto 0) & ld_data_mem(15 downto 8);
            when '1' =>     
                ld_data_half <= ld_data_mem(23 downto 16) & ld_data_mem(31 downto 24);
            when others => null;
        end case;

        case exout_reg.adrs_reg(1 downto 0) is
            when "00" =>            
                ld_data_byte <= ld_data_mem(7 downto 0);
            when "01" =>           
                ld_data_byte <= ld_data_mem(15 downto 8);
            when "10" =>              
                ld_data_byte <= ld_data_mem(23 downto 16);
            when "11" =>           
                ld_data_byte <= ld_data_mem(31 downto 24);
            when others => null;
        end case;
    end process;

    --------------------------- address muxes end--------------------------	

    --------------------------- sign extension begin--------------------------
    -- MS: why do we have double signe extension?
    -- SA: what is a double sign extension?
    process( s_u, ld_data_half, ld_data_byte
    )
    begin
        if (s_u = '1') then

            ld_half_ext	<= std_logic_vector(resize(signed(ld_data_half), 32)); 
            ld_byte_ext <= std_logic_vector(resize(signed(ld_data_byte), 32));
        else         
            ld_half_ext	<= std_logic_vector(resize(unsigned(ld_data_half), 32));       
            ld_byte_ext <= std_logic_vector(resize(unsigned(ld_data_byte), 32));
            
        end if;
    end process;

    --------------------------- sign extension end--------------------------

    --------------------------- size muxe begin--------------------------
    process(ldt_type, ld_byte_ext, ld_half_ext, ld_data_word
    )
    begin
        case ldt_type is
            when word =>             
                ld_data_out			   <= ld_data_word;
            when half =>            
                ld_data_out			   <= ld_half_ext;
            when byte =>               
                ld_data_out			   <= ld_byte_ext;
            when others => null;
        end case;
    end process;
	dout.data_mem_data_out	<= ld_data_out;

	
    --------------------------- size muxe end--------------------------

    process(exout_not_reg, mem_write)
    begin
        byte_enable(3 downto 0)    <= (others => '0');
        sc_byte_enable(3 downto 0) <= (others => '0');
        gm_byte_enable(3 downto 0) <= (others => '0');
        case exout_not_reg.adrs(1 downto 0) is
            when "00" => byte_enable(0) <= exout_not_reg.lm_write_not_reg;
                sc_byte_enable(0) <= exout_not_reg.sc_write_not_reg;
                gm_byte_enable(0) <= mem_write;

            when "01" => byte_enable(1) <= exout_not_reg.lm_write_not_reg;
                sc_byte_enable(1) <= exout_not_reg.sc_write_not_reg;
                gm_byte_enable(1) <= mem_write;

            when "10" => byte_enable(2) <= exout_not_reg.lm_write_not_reg;
                sc_byte_enable(2) <= exout_not_reg.sc_write_not_reg;
                gm_byte_enable(2) <= mem_write;

            when "11" => byte_enable(3) <= exout_not_reg.lm_write_not_reg;
                sc_byte_enable(3) <= exout_not_reg.sc_write_not_reg;
                gm_byte_enable(3) <= mem_write;
            when others => null;
        end case;
    end process;

    process(exout_not_reg, mem_write)
    begin
        word_enable    <= (others => '0');
        sc_word_enable <= (others => '0');
        sc_word_enable <= (others => '0');
        gm_word_enable <= (others => '0');
        case exout_not_reg.adrs(1) is
            when '0' => word_enable(0) <= exout_not_reg.lm_write_not_reg;
                sc_word_enable(0)      <= exout_not_reg.sc_write_not_reg;
                gm_word_enable(0)      <= mem_write;
            when '1' => word_enable(1) <= exout_not_reg.lm_write_not_reg;
                sc_word_enable(1)      <= exout_not_reg.sc_write_not_reg;
                gm_word_enable(1)      <= mem_write;
            when others => null;
        end case;
    end process;

    process(word_enable, byte_enable, decdout, exout_not_reg, mem_write, sc_word_enable,
    	gm_word_enable, gm_byte_enable, sc_byte_enable
    )
    begin
        case decdout.adrs_type is
            when word =>
                en(3 downto 0)    <= exout_not_reg.lm_write_not_reg & exout_not_reg.lm_write_not_reg & exout_not_reg.lm_write_not_reg & exout_not_reg.lm_write_not_reg;
                gm_en(3 downto 0) <= mem_write & mem_write & mem_write & mem_write;
                sc_en(3 downto 0) <= exout_not_reg.sc_write_not_reg & exout_not_reg.sc_write_not_reg & exout_not_reg.sc_write_not_reg & exout_not_reg.sc_write_not_reg;

                mem_write_data <= exout_not_reg.mem_write_data(7 downto 0) & exout_not_reg.mem_write_data(15 downto 8)
                								 & exout_not_reg.mem_write_data(23 downto 16) & exout_not_reg.mem_write_data(31 downto 24);
            when half =>
                en(3 downto 2) <= word_enable(1) & word_enable(1);
                en(1 downto 0) <= word_enable(0) & word_enable(0);

                gm_en(3 downto 2) <= gm_word_enable(1) & gm_word_enable(1);
                gm_en(1 downto 0) <= gm_word_enable(0) & gm_word_enable(0);

                sc_en(3 downto 2) <= sc_word_enable(1) & sc_word_enable(1);
                sc_en(1 downto 0) <= sc_word_enable(0) & sc_word_enable(0);

                mem_write_data <= exout_not_reg.mem_write_data(7 downto 0) & exout_not_reg.mem_write_data(15 downto 8)
                				  & exout_not_reg.mem_write_data(7 downto 0) & exout_not_reg.mem_write_data(15 downto 8);
            when byte =>
                en(3 downto 0) <= byte_enable(3 downto 0);

                gm_en(3 downto 0) <= gm_byte_enable(3 downto 0);

                sc_en(3 downto 0) <= sc_byte_enable(3 downto 0);

                mem_write_data   <= exout_not_reg.mem_write_data(7 downto 0) & exout_not_reg.mem_write_data(7 downto 0)
                					 & exout_not_reg.mem_write_data(7 downto 0) & exout_not_reg.mem_write_data(7 downto 0);
            when others => null;
        end case;
    end process;

    -- write back with stall
    process(mem_data_out_muxed, exout_reg, ld_data, ld_data_out, stall, prev_ld_data)
    begin
            if exout_reg.mem_to_reg = '1' then
                dout.data <=  mem_data_out_muxed;
                datain    <=    mem_data_out_muxed;
            else
                dout.data <= exout_reg.alu_result_reg;
                datain    <= exout_reg.alu_result_reg;
            end if;
            
            if (stall = '1') then
            	if exout_reg.mem_to_reg = '1' then
	                dout.data <= prev_mem_data_out_muxed; --mem_data_out_muxed; --
	                datain <= prev_mem_data_out_muxed; --mem_data_out_muxed;--
	            else
	                dout.data <= exout_reg.alu_result_reg;
	                datain <= exout_reg.alu_result_reg;
	            end if;
            end if;
    end process;
end arch;