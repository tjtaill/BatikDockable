package batikdockable;


import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.ViewBox;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.swing.JSVGScrollPane;
import org.apache.batik.swing.svg.LinkActivationEvent;
import org.apache.batik.swing.svg.LinkActivationListener;
import org.apache.batik.util.XMLResourceDescriptor;
import org.gjt.sp.jedit.*;
import org.gjt.sp.jedit.gui.DefaultFocusComponent;
import org.gjt.sp.jedit.msg.BufferUpdate;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.svg.*;

import javax.swing.*;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.NavigableMap;
import java.util.TreeMap;

public class BatikDockable extends JPanel implements EBComponent, DefaultFocusComponent {

    private final static String FILL_NONE = "#000000";
    private final static String XLINK_NS = "http://www.w3.org/1999/xlink";

    private CustomSvgCanvas customSvgCanvas;
    private JSVGScrollPane scrollPane;
    private org.gjt.sp.jedit.textarea.TextArea textArea;
    private Buffer buffer;
    private LinkLineJumpListener linkLineJumpListener;
    private NavigableMap<Integer, Element> textElements = new TreeMap<>();
    private Element lastColoredText;
    private SAXSVGDocumentFactory svgDocumentFactory =
            new SAXSVGDocumentFactory( XMLResourceDescriptor.getXMLParserClassName() );
    private int lastLineNumber = 0;
    private SVGDocument svgDocument;
    private SyncCaretListener syncCaretListener;
    private SVGSVGElement rootElement;
    private float[] viewBox;
    private SVGRect viewport;


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


        private float getScreenX(Element element) {
            SVGLocatable svgLocatable = (SVGLocatable) element;
            SVGRect bBox = svgLocatable.getBBox();

            float x = bBox.getX() + bBox.getWidth()/2;
            float y = bBox.getY() +  bBox.getHeight()/2;
            SVGMatrix ctm = svgLocatable.getCTM();

            return (ctm.getA() * x) + (ctm.getC() * y)  + ctm.getE();
        }

        private float getScreenY(Element element) {
            SVGLocatable svgLocatable = (SVGLocatable) element;
            SVGRect bBox = svgLocatable.getBBox();

            float x = bBox.getX() + bBox.getWidth()/2;
            float y = bBox.getY() +  bBox.getHeight()/2;
            SVGMatrix ctm = svgLocatable.getCTM();

            return (ctm.getB() * x) + (ctm.getD() * y)  + ctm.getF();
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
                    }
                };
                invokeLater(r);


            final Element textToColor = getEelement(line+1);
            if ( textToColor != null ) {
                r = new Runnable() {
                    @Override
                    public void run() {
                        textToColor.setAttributeNS(null, "fill", "orange");
                        /*
                        float screenX = getScreenX(textToColor);
                        float screenY = getScreenY(textToColor);

                        AffineTransform tx = AffineTransform.getTranslateInstance(-screenX, -screenY);
                        AffineTransform rt = (AffineTransform) customSvgCanvas.getRenderingTransform().clone();

                        Dimension canvasSize = customSvgCanvas.getSize();

                        tx.preConcatenate(AffineTransform.getTranslateInstance
                                (canvasSize.width/2, canvasSize.height/2));

                        rt.preConcatenate(tx);

                        customSvgCanvas.setRenderingTransform(rt);

                        */

                        /*
                        GraphicsNode gn = customSvgCanvas.getBridgeContxt().getGraphicsNode(textToColor);
                        AffineTransform rt = customSvgCanvas.getRenderingTransform();
                        Rectangle gnb = rt.createTransformedShape(gn.getBounds()).getBounds();


                        Dimension canvasSize = customSvgCanvas.getSize();

                        AffineTransform tx = AffineTransform.getTranslateInstance
                                (-gnb.getX() - gnb.getWidth() / 2,
                                        -gnb.getY() - gnb.getHeight() / 2);
                        tx.preConcatenate(AffineTransform.getTranslateInstance
                                (canvasSize.width/2, canvasSize.height/2));

                        AffineTransform newRT = new AffineTransform(rt);
                        newRT.preConcatenate(tx);
                        customSvgCanvas.setRenderingTransform(newRT);
                        */
                    }
                };
                invokeLater(r);
            }
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

        rootElement = svgDocument.getRootElement();


        viewBox = ViewBox.parseViewBoxAttribute(rootElement,
                rootElement.getAttributeNS(null, "viewBox"),
                customSvgCanvas.getBridgeContxt());


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
        customSvgCanvas.setSVGDocument( svgDocument );
        customSvgCanvas.addLinkActivationListener(new LinkLineJumpListener());
        syncCaretListener = new SyncCaretListener();
        textArea.addCaretListener( syncCaretListener );
        EditBus.addToBus(this);
        invalidate();
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
                textArea.removeCaretListener( syncCaretListener );
                EditBus.removeFromBus(this);
                customSvgCanvas.removeLinkActivationListener(linkLineJumpListener);
                buffer = null;
                textArea = null;
            }
        }
    }
}
