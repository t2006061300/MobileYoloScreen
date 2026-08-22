package com.example.sudokulive;

public final class SudokuSolver {
    private SudokuSolver() {}

    public static boolean isValidPuzzle(int[][] grid) {
        if (grid == null || grid.length != 9) return false;
        for (int r = 0; r < 9; r++) {
            if (grid[r] == null || grid[r].length != 9) return false;
            boolean[] seen = new boolean[10];
            for (int c = 0; c < 9; c++) {
                int v = grid[r][c];
                if (v < 0 || v > 9) return false;
                if (v != 0 && seen[v]) return false;
                if (v != 0) seen[v] = true;
            }
        }
        for (int c = 0; c < 9; c++) {
            boolean[] seen = new boolean[10];
            for (int r = 0; r < 9; r++) {
                int v = grid[r][c];
                if (v != 0 && seen[v]) return false;
                if (v != 0) seen[v] = true;
            }
        }
        for (int br = 0; br < 3; br++) {
            for (int bc = 0; bc < 3; bc++) {
                boolean[] seen = new boolean[10];
                for (int dr = 0; dr < 3; dr++) {
                    for (int dc = 0; dc < 3; dc++) {
                        int v = grid[br * 3 + dr][bc * 3 + dc];
                        if (v != 0 && seen[v]) return false;
                        if (v != 0) seen[v] = true;
                    }
                }
            }
        }
        return true;
    }

    public static int countGivens(int[][] grid) {
        int count = 0;
        for (int[] row : grid) for (int v : row) if (v != 0) count++;
        return count;
    }

    public static int[][] copy(int[][] src) {
        int[][] out = new int[9][9];
        for (int r = 0; r < 9; r++) System.arraycopy(src[r], 0, out[r], 0, 9);
        return out;
    }

    public static boolean solve(int[][] grid) {
        int bestR = -1, bestC = -1, bestMask = 0, bestCount = 10;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (grid[r][c] != 0) continue;
                int mask = candidateMask(grid, r, c);
                int count = Integer.bitCount(mask);
                if (count == 0) return false;
                if (count < bestCount) {
                    bestCount = count;
                    bestR = r;
                    bestC = c;
                    bestMask = mask;
                    if (count == 1) break;
                }
            }
            if (bestCount == 1) break;
        }
        if (bestR == -1) return true;

        for (int n = 1; n <= 9; n++) {
            if ((bestMask & (1 << n)) == 0) continue;
            grid[bestR][bestC] = n;
            if (solve(grid)) return true;
        }
        grid[bestR][bestC] = 0;
        return false;
    }

    public static int countSolutions(int[][] grid, int limit) {
        if (limit <= 0) return 0;
        return countSolutionsInternal(grid, limit);
    }

    private static int countSolutionsInternal(int[][] grid, int limit) {
        int bestR = -1, bestC = -1, bestMask = 0, bestCount = 10;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (grid[r][c] != 0) continue;
                int mask = candidateMask(grid, r, c);
                int count = Integer.bitCount(mask);
                if (count == 0) return 0;
                if (count < bestCount) {
                    bestCount = count;
                    bestR = r;
                    bestC = c;
                    bestMask = mask;
                    if (count == 1) break;
                }
            }
            if (bestCount == 1) break;
        }
        if (bestR == -1) return 1;

        int total = 0;
        for (int n = 1; n <= 9; n++) {
            if ((bestMask & (1 << n)) == 0) continue;
            grid[bestR][bestC] = n;
            total += countSolutionsInternal(grid, limit - total);
            grid[bestR][bestC] = 0;
            if (total >= limit) return total;
        }
        return total;
    }

    private static int candidateMask(int[][] grid, int row, int col) {
        int used = 0;
        for (int i = 0; i < 9; i++) {
            used |= 1 << grid[row][i];
            used |= 1 << grid[i][col];
        }
        int br = (row / 3) * 3;
        int bc = (col / 3) * 3;
        for (int r = br; r < br + 3; r++) {
            for (int c = bc; c < bc + 3; c++) used |= 1 << grid[r][c];
        }
        int all = 0;
        for (int n = 1; n <= 9; n++) all |= 1 << n;
        return all & ~used;
    }
}
