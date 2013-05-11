--
-- Copyright: 2013, Technical University of Denmark, DTU Compute
-- Author: Martin Schoeberl (martin@jopdesign.com)
-- License: Simplified BSD License
--

-- VHDL top level for Patmos in Chisel
--
-- Includes some 'magic' VHDL code to generate a reset after FPGA configuration.
--

library ieee;
use ieee.std_logic_1164.all;
use ieee.numeric_std.all;

entity patmos_top is
	port(
		clk : in  std_logic;
		led : out std_logic_vector(7 downto 0);
		txd : out std_logic;
		rxd : in  std_logic
	);
end entity patmos_top;

architecture rtl of patmos_top is
	component Patmos is
		port(
			clk             : in  std_logic;
			reset           : in  std_logic;
			io_dummy        : out std_logic_vector(31 downto 0);
			io_led          : out std_logic_vector(7 downto 0);
			io_uart_address : out std_logic;
			io_uart_wr_data : out std_logic_vector(31 downto 0);
			io_uart_rd      : out std_logic;
			io_uart_wr      : out std_logic;
			io_uart_rd_data : in  std_logic_vector(31 downto 0)
		);
	end component;

	-- DE2-70: 50 MHz clock => 100 MHz
	-- BeMicro: 16 MHz clock => 32 MHz
	constant clk_freq : integer := 32000000;
	constant pll_mult : natural := 10;
	constant pll_div  : natural := 5;

	signal clk_int : std_logic;

	-- for generation of internal reset
	signal int_res            : std_logic;
	signal res_reg1, res_reg2 : std_logic;
	signal res_cnt            : unsigned(2 downto 0) := "000"; -- for the simulation

	attribute altera_attribute : string;
	attribute altera_attribute of res_cnt : signal is "POWER_UP_LEVEL=LOW";

	-- just for now using the VHDL UART
	signal io_uart_address : std_logic;
	signal io_uart_wr_data : std_logic_vector(31 downto 0);
	signal io_uart_rd      : std_logic;
	signal io_uart_wr      : std_logic;
	signal io_uart_rd_data : std_logic_vector(31 downto 0);

begin
	pll_inst : entity work.pll generic map(
			multiply_by => pll_mult,
			divide_by   => pll_div
		)
		port map(
			inclk0 => clk,
			c0     => clk_int,
			c1     => open
		);
	-- we use a PLL
	-- clk_int <= clk;

	--
	--	internal reset generation
	--	should include the PLL lock signal
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

	comp : Patmos port map(
			clk_int, int_res, open, led,
			io_uart_address,
			io_uart_wr_data,
			io_uart_rd,
			io_uart_wr,
			io_uart_rd_data
		);

	ua : entity work.uart generic map(
			clk_freq  => clk_freq,
			baud_rate => 115200,
			txf_depth => 1,
			rxf_depth => 1
		)
		port map(
			clk     => clk_int,
			reset   => int_res,
			address => io_uart_address,
			wr_data => io_uart_wr_data,
			rd      => io_uart_rd,
			wr      => io_uart_wr,
			rd_data => io_uart_rd_data,
			txd     => txd,
			rxd     => rxd
		);

end architecture rtl;
