package io.github.markpollack.loopy.tui;

import com.williamcallahan.tui4j.compat.bubbletea.Command;
import com.williamcallahan.tui4j.compat.bubbletea.KeyPressMessage;
import com.williamcallahan.tui4j.compat.bubbletea.Message;
import com.williamcallahan.tui4j.compat.bubbletea.Model;
import com.williamcallahan.tui4j.compat.bubbletea.UpdateResult;
import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Animated infinity-loop logo splash screen using braille art.
 * <p>
 * Phase 1: Draw-on — braille characters bloom outward from the crossing center. Phase 2:
 * Color wave — a flowing purple-to-cyan gradient sweeps across the symbol. Any keypress
 * transitions to the next screen.
 */
public class LogoScreen implements Model {

	private static final Duration DRAW_FRAME_DURATION = Duration.ofMillis(35);

	private static final Duration WAVE_FRAME_DURATION = Duration.ofMillis(80);

	private static final char BRAILLE_BLANK = '\u2800';

	// @formatter:off
	private static final String[] LOGO_LINES = {
		"⠀⠀⠀⠀⣀⣤⣴⣶⣶⣦⣄⡀⠀⠀⠀⠀⠀⠀⢀⣤⣶⣶⣶⣦⣤⡀⠀⠀⠀⠀",
		"⠀⠀⢀⣾⣿⣿⣿⣿⣿⣿⣿⣷⣄⠀⠀⠀⢀⣾⣿⣿⣿⣿⣿⣿⣿⣿⣦⡀⠀⠀",
		"⠀⠀⣾⣿⠟⠋⠉⠀⠀⠉⠙⠻⣿⣷⡀⣰⣿⣿⣿⠟⠉⠀⠀⠀⠈⠙⣿⣷⠀⠀",
		"⠀⢸⣿⠏⠀⠀⠀⠀⠀⠀⠀⠀⠈⢻⣿⣿⣿⡿⠃⠀⠀⠀⠀⠀⠀⠀⠸⣿⡇⠀",
		"⠀⢸⣿⠀⠀⠀⠀⠀⠀⠀⠀⠀⢀⣾⣿⣿⡿⠁⠀⠀⠀⠀⠀⠀⠀⠀⠀⣿⡇⠀",
		"⠀⢸⣿⡆⠀⠀⠀⠀⠀⠀⠀⢀⣾⣿⣿⣿⣧⡀⠀⠀⠀⠀⠀⠀⠀⠀⢰⣿⡇⠀",
		"⠀⠀⢿⣿⣄⡀⠀⠀⠀⢀⣴⣿⣿⣿⠟⠘⢿⣿⣦⣀⡀⠀⠀⢀⣀⣴⣿⡿⠀⠀",
		"⠀⠀⠈⠻⣿⣿⣿⣿⣿⣿⣿⣿⡿⠁⠀⠀⠀⠙⣿⣿⣿⣿⣿⣿⣿⣿⡿⠁⠀⠀",
		"⠀⠀⠀⠀⠈⠛⠻⠿⠿⠿⠛⠁⠀⠀⠀⠀⠀⠀⠈⠙⠻⠿⠿⠿⠛⠉⠀⠀⠀⠀",
	};

	private static final String[] GRADIENT = {
		"#E040FB", "#CE43EC", "#AB47BC", "#9C4DC4",
		"#7E57C2", "#6A5FC0", "#5C6BC0", "#4E78C4",
		"#42A5F5", "#34B4E4", "#26C6DA", "#26B8C8",
		"#26A69A", "#26B8C8", "#26C6DA", "#34B4E4",
		"#42A5F5", "#4E78C4", "#5C6BC0", "#6A5FC0",
		"#7E57C2", "#9C4DC4", "#AB47BC", "#CE43EC",
	};
	// @formatter:on

	private static final Style[] GRADIENT_STYLES = buildGradientStyles();

	private static final Style DRAW_STYLE = Style.newStyle().foreground(Color.color("#7E57C2"));

	private static final Style TEXT_STYLE = Style.newStyle().foreground(Color.color("#AB47BC")).bold(true);

	private static final Style HINT_STYLE = Style.newStyle().foreground(Color.color("#555555"));

	private static final int[][] DRAW_ORDER = buildDrawOrder();

	private record LogoTickMessage(LocalDateTime time) implements Message {
	}

	private final Supplier<Model> nextScreenFactory;

	private int drawIndex;

	private boolean drawComplete;

	private int waveOffset;

	private final boolean[][] revealed;

	public LogoScreen(Supplier<Model> nextScreenFactory) {
		this.nextScreenFactory = nextScreenFactory;
		this.revealed = new boolean[LOGO_LINES.length][];
		for (int r = 0; r < LOGO_LINES.length; r++) {
			this.revealed[r] = new boolean[LOGO_LINES[r].length()];
		}
	}

	@Override
	public Command init() {
		return () -> new LogoTickMessage(LocalDateTime.now());
	}

	@Override
	public UpdateResult<? extends Model> update(Message msg) {
		if (msg instanceof KeyPressMessage) {
			Model next = this.nextScreenFactory.get();
			return UpdateResult.from(next, next.init());
		}

		if (msg instanceof LogoTickMessage) {
			if (!drawComplete) {
				int charsPerTick = 4;
				for (int i = 0; i < charsPerTick && drawIndex < DRAW_ORDER.length; i++) {
					int[] pos = DRAW_ORDER[drawIndex];
					this.revealed[pos[0]][pos[1]] = true;
					drawIndex++;
				}
				if (drawIndex >= DRAW_ORDER.length) {
					drawComplete = true;
				}
				return UpdateResult.from(this, Command.tick(DRAW_FRAME_DURATION, LogoTickMessage::new));
			}
			else {
				this.waveOffset = (this.waveOffset + 1) % GRADIENT.length;
				return UpdateResult.from(this, Command.tick(WAVE_FRAME_DURATION, LogoTickMessage::new));
			}
		}

		return UpdateResult.from(this);
	}

	@Override
	public String view() {
		StringBuilder sb = new StringBuilder();
		sb.append("\n");

		for (int r = 0; r < LOGO_LINES.length; r++) {
			sb.append("  ");
			String line = LOGO_LINES[r];

			if (drawComplete) {
				renderWaveLine(sb, line, r);
			}
			else {
				renderDrawLine(sb, line, r);
			}
			sb.append("\n");
		}

		sb.append("\n");
		if (drawComplete) {
			sb.append("          ").append(TEXT_STYLE.render("l  o  o  p  y")).append("\n\n");
			sb.append("    ").append(HINT_STYLE.render("press any key to continue")).append("\n");
		}

		return sb.toString();
	}

	/**
	 * Renders a line during the color wave phase. Each non-blank character is colored
	 * based on its column position plus the wave offset.
	 */
	private void renderWaveLine(StringBuilder sb, String line, int row) {
		for (int c = 0; c < line.length(); c++) {
			char ch = line.charAt(c);
			if (ch == BRAILLE_BLANK) {
				sb.append(ch);
			}
			else {
				int idx = Math.floorMod(c - this.waveOffset, GRADIENT.length);
				sb.append(GRADIENT_STYLES[idx].render(String.valueOf(ch)));
			}
		}
	}

	/**
	 * Renders a line during the draw-on phase. Revealed characters are shown in the draw
	 * style; unrevealed positions show braille blanks.
	 */
	private void renderDrawLine(StringBuilder sb, String line, int row) {
		StringBuilder run = new StringBuilder();
		for (int c = 0; c < line.length(); c++) {
			char ch = line.charAt(c);
			if (ch == BRAILLE_BLANK || !this.revealed[row][c]) {
				if (run.length() > 0) {
					sb.append(DRAW_STYLE.render(run.toString()));
					run.setLength(0);
				}
				sb.append(BRAILLE_BLANK);
			}
			else {
				run.append(ch);
			}
		}
		if (run.length() > 0) {
			sb.append(DRAW_STYLE.render(run.toString()));
		}
	}

	private static Style[] buildGradientStyles() {
		Style[] styles = new Style[GRADIENT.length];
		for (int i = 0; i < GRADIENT.length; i++) {
			styles[i] = Style.newStyle().foreground(Color.color(GRADIENT[i]));
		}
		return styles;
	}

	/**
	 * Builds draw order by sorting non-blank cells by distance from the crossing center
	 * (bloom effect).
	 */
	private static int[][] buildDrawOrder() {
		double centerRow = 4.0;
		double centerCol = 14.5;

		List<int[]> positions = new ArrayList<>();
		for (int r = 0; r < LOGO_LINES.length; r++) {
			String line = LOGO_LINES[r];
			for (int c = 0; c < line.length(); c++) {
				if (line.charAt(c) != BRAILLE_BLANK) {
					positions.add(new int[] { r, c });
				}
			}
		}

		positions.sort((a, b) -> {
			double dA = Math.pow(a[0] - centerRow, 2) + Math.pow(a[1] - centerCol, 2);
			double dB = Math.pow(b[0] - centerRow, 2) + Math.pow(b[1] - centerCol, 2);
			return Double.compare(dA, dB);
		});

		return positions.toArray(new int[0][]);
	}

}
