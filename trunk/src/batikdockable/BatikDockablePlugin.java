package batikdockable;

import org.gjt.sp.jedit.EditPlugin;
import org.gjt.sp.jedit.jEdit;

public class BatikDockablePlugin extends EditPlugin {
	private static final String SYNC_SVG_WITH_BUFFER_PROP = "plugin.batikdockable.syncsvg";


    public void start() {
	}

	public void stop() {
	}

	public static  void toggleSyncSvgWithBuffer() {
		boolean status = jEdit.getBooleanProperty(SYNC_SVG_WITH_BUFFER_PROP);
		jEdit.setBooleanProperty(SYNC_SVG_WITH_BUFFER_PROP, !status);
	}

	public static boolean isSyncSvgWithBuffer() {
		return jEdit.getBooleanProperty(SYNC_SVG_WITH_BUFFER_PROP);
	}
}