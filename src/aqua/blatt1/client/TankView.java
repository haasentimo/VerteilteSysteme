package aqua.blatt1.client;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Observable;
import java.util.Observer;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import aqua.blatt1.common.FishModel;

@SuppressWarnings("serial")
public class TankView extends JPanel implements Observer {
	private final TankModel tankModel;
	private final FishView fishView;
	private final Runnable repaintRunnable;

	public TankView(final TankModel tankModel) {
		this.tankModel = tankModel;
		fishView = new FishView();

		repaintRunnable = new Runnable() {
			@Override
			public void run() {
				repaint();
			}
		};

		setPreferredSize(new Dimension(TankModel.WIDTH, TankModel.HEIGHT));
		setBackground(new Color(175, 200, 235));

		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				tankModel.newFish(e.getX(), e.getY());
			}
		});
	}


	private void drawBorders(Graphics2D g2d) {
		final int SIZE = 2;
		g2d.setColor(Color.BLACK);
		g2d.fill(new Rectangle(SIZE, SIZE, SIZE, TankModel.HEIGHT - SIZE * 2));
		g2d.fill(new Rectangle(TankModel.WIDTH - SIZE * 2, SIZE, SIZE, TankModel.HEIGHT - SIZE * 2));
	}

	private void doDrawing(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;

		for (FishModel fishModel : tankModel) {
			g2d.drawImage(fishView.getImage(fishModel), fishModel.getX(), fishModel.getY(), null);
			g2d.drawString(fishModel.getId(), fishModel.getX(), fishModel.getY());
		}

	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		doDrawing(g);
		if (!tankModel.hasToken) {
			drawBorders((Graphics2D) g);
		}
	}

	@Override
	public void update(Observable o, Object arg) {
		SwingUtilities.invokeLater(repaintRunnable);
	}
}
