package batikdockable;


import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.ViewBox;
import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.swing.gvt.GVTTreeRendererEvent;
import org.apache.batik.swing.gvt.GVTTreeRendererListener;
import org.apache.batik.swing.svg.LinkActivationEvent;
import org.apache.batik.swing.svg.LinkActivationListener;
import org.apache.batik.util.XMLResourceDescriptor;
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGLocatable;
import org.w3c.dom.svg.SVGRect;
import org.w3c.dom.svg.SVGSVGElement;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import java.awt.*;
import java.io.File;
import java.util.NavigableMap;
import java.util.TreeMap;

public class BatikDockable extends JPanel implements EBComponent, DefaultFocusComponent {

    private final static String FILL_NONE = "#000000";
    private final static String XLINK_NS = "http://www.w3.org/1999/xlink";

    private CustomSvgCanvas customSvgCanvas;
    private org.gjt.sp.jedit.textarea.TextArea textArea;
    private Buffer buffer;
    private LinkLineJumpListener linkLineJumpListener;
    private NavigableMap<Integer, Element> textElements = new TreeMap<>();
    private SAXSVGDocumentFactory svgDocumentFactory =
            new SAXSVGDocumentFactory( XMLResourceDescriptor.getXMLParserClassName() );
    private SVGDocument svgDocument;
    private SyncCaretListener syncCaretListener;
    private SVGSVGElement rootElement;
    private RefreshSizeRenderListener refreshSizeRenderListener;
    private float viewBoxCenterX;
    private float viewBoxCenterY;
    private float viewBoxWidth;
    private float viewBoxHeight;


    private class LinkLineJumpListener implements LinkActivationListener {

        @Override
        public void linkActivated(LinkActivationEvent linkActivationEvent) {
            if ( textArea == null ) return;

            int lineNumber = Integer.valueOf(linkActivationEvent.getReferencedURI());
            textArea.setCaretPosition(textArea.getLineStartOffset(lineNumber - 1));
        }
    }

    private class SyncCaretListener implements CaretListener {
        private int oldLine = -1;

        private Element getEelement(int line) {
            NavigableMap<Integer, Element> headMap = textElements.headMap(line, true);
            return !headMap.isEmpty() ? headMap.lastEntry().getValue() : null;
        }

        private void invokeLater(Runnable runnable) {
            customSvgCanvas.getUpdateManager().getUpdateRunnableQueue().invokeLater(runnable);
        }

        @Override
        public void caretUpdate(CaretEvent e) {
            int offset = e.getDot();
            int line = textArea.getLineOfOffset(offset);

            if ( line == oldLine ) return;

            oldLine = line;


                Runnable r  = new Runnable() {
                    @Override
                    public void run() {
                        for(Element value : textElements.values() ) {
                            value.setAttributeNS(null, "fill", FILL_NONE);

                        }
                        rootElement.removeAttributeNS(null, "transform");
                    }
                };
                invokeLater(r);


            final Element textToColor = getEelement(line+1);
            if ( textToColor != null ) {
                r = new Runnable() {
                    @Override
                    public void run() {
                        textToColor.setAttributeNS(null, "fill", "orange");


                        /* TODO: Jonathan's suggestion to make more general
                        SVGRect textBBox = ((SVGLocatable)textElement).getBBox();
                        SVGPoint textCenter = root.createSVGPoint();
                        textCenter.setX(textBBox.getX() + (textBBox.getWidth()/2));
                        textCenter.setY(textBBox.getY() + (textBBox.getHeight()/2));
                        and then ...
                        SVGMatrix matrix = ((SVGLocatable)textElement).getTransformToElement(root);
                        SVGPoint textOnSVGPoint = textCenter.matrixTransform(matrix);
                        */

                        /*
                        customSvgCanvas.setRenderingTransform( customSvgCanvas.getInitialTransform(), true);

                        SVGLocatable textLoc = (SVGLocatable) textToColor;

                        SVGRect bounds = textLoc.getBBox();

                        float dx = bounds.getX() + bounds.getWidth() / 2;
                        float dy = bounds.getY() + bounds.getHeight() / 2;

                        final SVGSVGElement rootElement = svgDocument.getRootElement();

                        final float tx = dx - viewBoxCenterX;
                        final float ty = dy - viewBoxCenterY;
                        rootElement.setAttributeNS(null, "viewBox", tx + " " + ty + " " + viewBoxWidth + " " + viewBoxHeight  );
                        */
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
        add(customSvgCanvas, BorderLayout.CENTER);
    }

    public void openSvg(EditPane editPane) {
        this.buffer = editPane.getBuffer();
        this.textArea = editPane.getTextArea();
        Buffer buffer = editPane.getBuffer();
        String[] parts = buffer.getPath().split("\\.", 2);
        File svgFile = new File( parts[0] + ".svg" );

        try {
            svgDocument = svgDocumentFactory.createSVGDocument( svgFile.toURI().toString() );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        customSvgCanvas.setSVGDocument( svgDocument );

        rootElement = svgDocument.getRootElement();

        float[] viewBoxAttrs = ViewBox.parseViewBoxAttribute(rootElement,
                rootElement.getAttributeNS(null, "viewBox"), customSvgCanvas.getBridgeContxt());

        viewBoxCenterX = viewBoxAttrs[0] + viewBoxAttrs[2] / 2;
        viewBoxCenterY = viewBoxAttrs[1] + viewBoxAttrs[3] / 2;
        viewBoxWidth = viewBoxAttrs[2];
        viewBoxHeight = viewBoxAttrs[3];

        NodeList anchors = svgDocument.getElementsByTagName("a");
        textElements.clear();
        for(int i = 0; i < anchors.getLength(); i++) {
            Element anchor = (Element) anchors.item(i);
            Attr href = anchor.getAttributeNodeNS(XLINK_NS, "href");
            Integer lineNumber = Integer.valueOf(href.getValue());
            NodeList textNodes = anchor.getElementsByTagName("text");
            if ( textNodes.getLength() != 1 ) continue;
            Element text = (Element) textNodes.item(0);
            textElements.put(lineNumber, text);
        }

        refreshSizeRenderListener = new RefreshSizeRenderListener();
        customSvgCanvas.addGVTTreeRendererListener( refreshSizeRenderListener );

        customSvgCanvas.addLinkActivationListener(new LinkLineJumpListener());
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
