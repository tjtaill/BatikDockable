package batikdockable;


import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.swing.gvt.GVTTreeRendererEvent;
import org.apache.batik.swing.gvt.GVTTreeRendererListener;
import org.apache.batik.swing.svg.LinkActivationEvent;
import org.apache.batik.swing.svg.LinkActivationListener;
import org.apache.batik.util.XMLResourceDescriptor;
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.gjt.sp.jedit.textarea.JEditTextArea;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.svg.*;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BatikDockable extends JPanel implements EBComponent, DefaultFocusComponent {

    private final static String FILL_NONE = "#000000";
    private final static String XLINK_NS = "http://www.w3.org/1999/xlink";
    private static Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private CustomSvgCanvas customSvgCanvas;

    private Buffer buffer;
    private LinkLineJumpListener linkLineJumpListener;
    private NavigableMap<Integer, Element> elements = new TreeMap<>();
    private SAXSVGDocumentFactory svgDocumentFactory =
            new SAXSVGDocumentFactory( XMLResourceDescriptor.getXMLParserClassName() );
    private SVGDocument svgDocument;
    private SyncCaretListener syncCaretListener;
    private SVGSVGElement rootElement;
    private RefreshSizeRenderListener refreshSizeRenderListener;
    private boolean syncSvgWithBuffer = true;
    private JEditTextArea textArea;


    private class LinkLineJumpListener implements LinkActivationListener {


        @Override
        public void linkActivated(LinkActivationEvent linkActivationEvent) {
            if ( textArea == null ) return;
            Matcher matcher = NUMBER_PATTERN.matcher( linkActivationEvent.getReferencedURI() );
            if ( matcher.find() ) {
                int lineNumber = Integer.valueOf( matcher.group(1) );
                textArea.setCaretPosition(textArea.getLineStartOffset(lineNumber - 1));
            }
        }
    }

    private class SyncCaretListener implements CaretListener {
        private int oldLine = -1;

        private Element getEelement(int line) {
            NavigableMap<Integer, Element> headMap = elements.headMap(line, true);
            return !headMap.isEmpty() ? headMap.lastEntry().getValue() : null;
        }

        private void invokeLater(Runnable runnable) {
            customSvgCanvas.getUpdateManager().getUpdateRunnableQueue().invokeLater(runnable);
        }

        @Override
        public void caretUpdate(CaretEvent e) {
            JEditTextArea ta = (JEditTextArea) e.getSource();

            if (buffer != ta.getBuffer()) return;

            int offset = e.getDot();
            int line = textArea.getLineOfOffset(offset);

            if (line == oldLine) return;

            oldLine = line;


            Runnable r = new Runnable() {
                @Override
                public void run() {
                    for (Element value : elements.values()) {
                        value.setAttributeNS(null, "fill", FILL_NONE);
                    }
                }
            };
            invokeLater(r);

            final Element element = getEelement(line + 1);

            if ( element != null && BatikDockablePlugin.isSyncSvgWithBuffer() ) {
                final SVGRect textBBox = ((SVGLocatable) element).getBBox();
                final SVGPoint textCenter = rootElement.createSVGPoint();
                textCenter.setX(textBBox.getX() + textBBox.getWidth() / 2);
                textCenter.setY(textBBox.getY() + textBBox.getHeight() / 2);

                final SVGMatrix rootTransform = ((SVGLocatable) element).getTransformToElement(rootElement);
                final SVGPoint textCenterOnRoot = textCenter.matrixTransform(rootTransform);

                String[] vbParts = rootElement.getAttributeNS(null, "viewBox").split(" ");
                float vbX = -(Float.parseFloat(vbParts[2]) / 2 - textCenterOnRoot.getX());
                float vbY = -(Float.parseFloat(vbParts[3]) / 2 - textCenterOnRoot.getY());

                // center this into canvas / screen coords ?
                final float svgX = vbX + Float.valueOf(vbParts[2]) / 2;
                final float svgY = vbY + Float.valueOf(vbParts[3]) / 2;

                Point2D.Float viewBoxCenter = new Point2D.Float(svgX, svgY);
                Point2D.Float canvasDestination = new Point2D.Float();

                AffineTransform vbTx = customSvgCanvas.getViewBoxTransform();

                vbTx.transform(viewBoxCenter, canvasDestination);

                Dimension size = customSvgCanvas.getSize();

                // caluclate delta for canvas center
                double dx = size.getWidth() / 2 - canvasDestination.getX();
                double dy = size.getHeight() / 2 - canvasDestination.getY();

                // merge transforms
                AffineTransform translateInstance = AffineTransform.getTranslateInstance(dx, dy);

                AffineTransform renderingTransform = (AffineTransform) customSvgCanvas.getRenderingTransform().clone();

                renderingTransform.preConcatenate(translateInstance);

                customSvgCanvas.setRenderingTransform(renderingTransform);
            }

            if (element != null) {
                r = new Runnable() {
                    @Override
                    public void run() {
                        element.setAttributeNS(null, "fill", "orange");
                    }
                };
                invokeLater(r);
            }
        }
    }

    private class RefreshSizeRenderListener implements GVTTreeRendererListener {

        @Override
        public void gvtRenderingPrepare(GVTTreeRendererEvent gvtTreeRendererEvent) {

        }

        @Override
        public void gvtRenderingStarted(GVTTreeRendererEvent gvtTreeRendererEvent) {

        }

        @Override
        public void gvtRenderingCompleted(GVTTreeRendererEvent gvtTreeRendererEvent) {
            invalidate();
            validate();
        }

        @Override
        public void gvtRenderingCancelled(GVTTreeRendererEvent gvtTreeRendererEvent) {

        }

        @Override
        public void gvtRenderingFailed(GVTTreeRendererEvent gvtTreeRendererEvent) {

        }
    }

    public BatikDockable() {
        customSvgCanvas = new CustomSvgCanvas();
        customSvgCanvas.setDoubleBuffered(true);
        customSvgCanvas.setDocumentState(JSVGCanvas.ALWAYS_DYNAMIC);
        setLayout( new BorderLayout());
        add(customSvgCanvas, BorderLayout.CENTER);
    }

    public void openSvg(EditPane editPane, String filePath) {
        this.buffer = editPane.getBuffer();
        textArea = editPane.getTextArea();
        Buffer buffer = editPane.getBuffer();
        File svgFile = new File( filePath );

        try {
            svgDocument = svgDocumentFactory.createSVGDocument( svgFile.toURI().toString() );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        customSvgCanvas.setSVGDocument( svgDocument );

        rootElement = svgDocument.getRootElement();

        NodeList anchors = svgDocument.getElementsByTagName("a");
        elements.clear();
        for(int i = 0; i < anchors.getLength(); i++) {
            Element anchor = (Element) anchors.item(i);
            Attr href = anchor.getAttributeNodeNS(XLINK_NS, "href");
            Matcher matcher = NUMBER_PATTERN.matcher(href.getValue());
            if ( matcher.find() ) {
                Integer lineNumber = Integer.valueOf(matcher.group(1));
                NodeList textNodes = anchor.getElementsByTagName("text");
                NodeList useNodes = anchor.getElementsByTagName("use");
                Element element = null;
                if (textNodes.getLength() == 1) {
                    element = (Element) textNodes.item(0);
                } else if ( useNodes.getLength() > 0 ) {
                    element = anchor;
                } else {
                    continue;
                }
                elements.put(lineNumber, element);
            }
        }

        refreshSizeRenderListener = new RefreshSizeRenderListener();
        customSvgCanvas.addGVTTreeRendererListener( refreshSizeRenderListener );
        linkLineJumpListener = new LinkLineJumpListener();
        customSvgCanvas.addLinkActivationListener(linkLineJumpListener);
        syncCaretListener = new SyncCaretListener();
        textArea.addCaretListener( syncCaretListener);
        EditBus.addToBus(this);

    }

    @Override
    public void focusOnDefaultComponent() {
        customSvgCanvas.requestFocus();
    }



    @Override
    public void handleMessage(EBMessage ebMessage) {
        if( ebMessage instanceof BufferUpdate && ebMessage.getSource() == buffer ) {
            BufferUpdate bufferUpdate = (BufferUpdate) ebMessage;
            if ( bufferUpdate.getWhat() == BufferUpdate.CLOSING ) {
                customSvgCanvas.removeGVTTreeRendererListener( refreshSizeRenderListener );
                textArea.removeCaretListener( syncCaretListener );
                EditBus.removeFromBus(this);
                customSvgCanvas.removeLinkActivationListener(linkLineJumpListener);
                buffer = null;
                textArea = null;
            }
        }
    }

}
