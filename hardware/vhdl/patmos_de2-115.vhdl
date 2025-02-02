--
-- Copyright: 2013, Technical University of Denmark, DTU Compute
-- Author: Martin Schoeberl (martin@jopdesign.com)
--         Rasmus Bo Soerensen (rasmus@rbscloud.dk)
-- License: Simplified BSD License
--

-- VHDL top level for Patmos in Chisel on Altera de2-115 board
--
-- Includes some 'magic' VHDL code to generate a reset after FPGA configuration.
--

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

entity patmos_top is
  port(
    clk : in  std_logic;
	 
    oLedsPins_led : out std_logic_vector(8 downto 0);
    iKeysPins_key : in std_logic_vector(3 downto 0);
	 
    oUartPins_txd : out std_logic;
    iUartPins_rxd : in  std_logic;
	 
    oSRAM_A : out std_logic_vector(19 downto 0);
    SRAM_DQ : inout std_logic_vector(15 downto 0);
    oSRAM_CE_N : out std_logic;
    oSRAM_OE_N : out std_logic;
    oSRAM_WE_N : out std_logic;
    oSRAM_LB_N : out std_logic;
    oSRAM_UB_N : out std_logic;
	 
    oAudioInterface_dacLrc : out std_logic;
    oAudioInterface_bclk : out std_logic;
    iAudioInterface_adcDat : in std_logic;
    oAudioInterface_dacDat : out std_logic;
    oAudioInterface_xclk : out std_logic;
    oAudioInterface_adcLrc : out std_logic;

    oAudioInterface_sclk : out std_logic;
    ioAudioInterface_sdat : inout std_logic
    
	 );
end entity patmos_top;

architecture rtl of patmos_top is
	component Patmos is
		port(
			clock           : in  std_logic;
			reset           : in  std_logic;

			io_Leds_led : out std_logic_vector(8 downto 0);
			io_Keys_key : in  std_logic_vector(3 downto 0);
			io_UartCmp_tx  : out std_logic;
			io_UartCmp_rx  : in  std_logic;

			io_SramCtrl_ramOut_addr : out std_logic_vector(19 downto 0);
			io_SramCtrl_ramOut_doutEna : out std_logic;
			io_SramCtrl_ramIn_din : in std_logic_vector(15 downto 0);
			io_SramCtrl_ramOut_dout : out std_logic_vector(15 downto 0);
			io_SramCtrl_ramOut_nce : out std_logic;
			io_SramCtrl_ramOut_noe : out std_logic;
			io_SramCtrl_ramOut_nwe : out std_logic;
			io_SramCtrl_ramOut_nlb : out std_logic;
			io_SramCtrl_ramOut_nub : out std_logic;

			io_AudioInterface_dacLrc : out std_logic;
			io_AudioInterface_bclk : out std_logic;
			io_AudioInterface_adcDat : in std_logic;
			io_AudioInterface_dacDat : out std_logic;
			io_AudioInterface_xclk : out std_logic;
			io_AudioInterface_adcLrc : out std_logic;
			
			io_AudioInterface_sclkOut : out std_logic;
			io_AudioInterface_we : out std_logic;
			io_AudioInterface_sdIn : in std_logic;
			io_AudioInterface_sdOut : out std_logic
    );
  end component;

  -- DE2-70: 50 MHz clock => 80 MHz
  -- BeMicro: 16 MHz clock => 25.6 MHz
  constant pll_infreq : real    := 50.0;
  constant pll_mult   : natural := 8;
  constant pll_div    : natural := 5;

  signal clk_int : std_logic;

  -- for generation of internal reset
  signal int_res            : std_logic;
  signal res_reg1, res_reg2 : std_logic;
  signal res_cnt            : unsigned(2 downto 0) := "000"; -- for the simulation

    -- sram signals for tristate inout
    signal sram_out_dout_ena : std_logic;
    signal sram_out_dout : std_logic_vector(15 downto 0);

	 -- audio interface signals for tristate inout
    signal io_AudioInterface_we : std_logic;
    signal io_AudioInterface_sdIn : std_logic;
    signal io_AudioInterface_sdOut : std_logic;
    
	 
  attribute altera_attribute : string;
  attribute altera_attribute of res_cnt : signal is "POWER_UP_LEVEL=LOW";

begin
  pll_inst : entity work.pll generic map(
      input_freq  => pll_infreq,
      multiply_by => pll_mult,
      divide_by   => pll_div
    )
    port map(
      inclk0 => clk,
      c0     => clk_int
    );
  -- we use a PLL
  -- clk_int <= clk;

  --
  --  internal reset generation
  --  should include the PLL lock signal
  --
  process(clk_int)
  begin
    if rising_edge(clk_int) then
      if (res_cnt /= "111") then
        res_cnt <= res_cnt + 1;
      end if;
      res_reg1 <= not res_cnt(0) or not res_cnt(1) or not res_cnt(2);
      res_reg2 <= res_reg1;
      int_res  <= res_reg2;
    end if;
  end process;


    -- tristate output to ssram
    process(sram_out_dout_ena, sram_out_dout)
    begin
      if sram_out_dout_ena='1' then
        SRAM_DQ <= sram_out_dout;
      else
        SRAM_DQ <= (others => 'Z');
      end if;
    end process;

	 -- tristate i/o 
    process(io_AudioInterface_we, io_AudioInterface_sdOut)
    begin
      if io_AudioInterface_we = '1' then
        ioAudioInterface_sdat <= io_AudioInterface_sdOut;
		  io_AudioInterface_sdIn <= '-';
      else
        ioAudioInterface_sdat <= 'Z';
        io_AudioInterface_sdIn <= ioAudioInterface_sdat;
      end if;
    end process;
	 
    comp : Patmos port map(clk_int, int_res,
           oLedsPins_led,
           iKeysPins_key,
           oUartPins_txd, iUartPins_rxd,
           oSRAM_A, sram_out_dout_ena, SRAM_DQ, sram_out_dout, oSRAM_CE_N, oSRAM_OE_N, oSRAM_WE_N, oSRAM_LB_N, oSRAM_UB_N,
			  oAudioInterface_dacLrc, oAudioInterface_bclk, iAudioInterface_adcDat, oAudioInterface_dacDat, oAudioInterface_xclk, oAudioInterface_adcLrc,
			  oAudioInterface_sclk, io_AudioInterface_we, io_AudioInterface_sdIn, io_AudioInterface_sdOut
			  );
			  

end architecture rtl;
