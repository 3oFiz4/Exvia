return {
	"Eutrius/Otree.nvim",
	lazy = false,
	dependencies = {
		-- Optional: Enhanced file operations
		"stevearc/oil.nvim",
		-- Optional: Icon support
		-- { "echasnovski/mini.icons", opts = {} },
		-- "nvim-tree/nvim-web-devicons",
	},
	config = function()
		require("Otree").setup()
	end,
}
