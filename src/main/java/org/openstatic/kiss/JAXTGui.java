package org.openstatic.kiss;

import javax.swing.JPopupMenu;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.Toolkit;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.net.URI;

import javax.imageio.ImageIO;
import javax.swing.JMenuItem;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;

public class JAXTGui implements ActionListener
{
    private SystemTray tray;
    private TrayIcon trayIcon;
    private BufferedImage tray_icon;
    private JMenuItem exit_item;
    private JMenuItem openinbrowser_item;
    private ImageIcon exitIcon;
    private ImageIcon browserIcon;
    private static JAXTGui instance;

    public JAXTGui()
    {
        JAXTGui.instance = this;
        this.openinbrowser_item = new JMenuItem("Open in Browser");
        this.openinbrowser_item.addActionListener(this);
        this.openinbrowser_item.setActionCommand("open");
       
        this.exit_item = new JMenuItem("Shutdown JAXT");
        this.exit_item.addActionListener(this);
        this.exit_item.setActionCommand("exit");
        try
        {
            this.browserIcon = new ImageIcon(ImageIO.read(getClass().getResourceAsStream("/jaxt/icon-64.png")));
            this.openinbrowser_item.setIcon(this.browserIcon);

            this.exitIcon = new ImageIcon(ImageIO.read(getClass().getResourceAsStream("/jaxt/quit.png")));
            this.exit_item.setIcon(this.exitIcon);

            if (SystemTray.isSupported())
            {
                this.tray = SystemTray.getSystemTray();
                Dimension st_d = this.tray.getTrayIconSize();
                double st_h = st_d.getHeight();
                double st_w = st_d.getWidth();
                String icon_file = null;
                if (st_h == 16 && st_w == 16)
                    icon_file = "/jaxt/icon-16.png";
                else if (st_h == 24 && st_w == 24)
                    icon_file = "/jaxt/icon-24.png";
                else
                    icon_file = "/jaxt/icon-32.png";
                //System.err.println("Icon File: " + icon_file);
                this.tray_icon = ImageIO.read(getClass().getResourceAsStream(icon_file));
                this.trayIcon = new TrayIcon(this.tray_icon, "JAXT", null);
                this.trayIcon.addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(MouseEvent e)
                    {
                        JAXTGui.this.showTrayPopup(e.getXOnScreen(), e.getYOnScreen());
                    }
                });
                try
                {
                    this.tray.add(this.trayIcon);
                } catch (Exception e) {
                    
                }
            } else {
                System.err.println("System Tray not available");
            }
        } catch (Exception ex) {
          System.out.println("Unable to load System Tray");
        }
    }

    public void showTrayPopup(int x, int y)
    {
        try
        {
            final JPopupMenu tray_popup = new JPopupMenu();
            tray_popup.add(this.openinbrowser_item);
            tray_popup.add(this.exit_item);
            tray_popup.setLocation(x,y);
            tray_popup.setInvoker(tray_popup);
            tray_popup.setVisible(true);
        } catch (Exception e) {
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) 
    {
        if (e.getSource() == this.exit_item)
        {
            System.exit(0);
        } else if (e.getSource() == this.openinbrowser_item) {
            browseTo("http://127.0.0.1:" + JavaKISSMain.settings.optInt("apiPort", 8101) + "/");
        }
    }

    public static boolean browseTo(String url)
    {
        try
        {
            Desktop dt = Desktop.getDesktop();
            dt.browse(new URI(url));
            return true;
        } catch (Exception dt_ex) {
            return false;
        }
    }
}
