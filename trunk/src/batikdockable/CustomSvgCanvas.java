package batikdockable;


import org.apache.batik.bridge.UserAgent;
import org.apache.batik.swing.JSVGCanvas;
import org.w3c.dom.svg.SVGAElement;

/*
This class is to disable JSVGCanvas default action when a hyper link
is clicked in an svg whi is to open that url this is undesired behavior
for this plugin to use the links as line numbers refering to textArea position
 */
class CustomSvgCanvas extends JSVGCanvas {
    class CustomCanvasUserAgent extends JSVGCanvas.CanvasUserAgent {
        @Override
        public void openLink(SVGAElement svgaElement) {
            String href =  svgaElement.getHref().getAnimVal();

            fireLinkActivatedEvent(svgaElement, href);
        }
    }

    @Override
    protected UserAgent createUserAgent() {
        return new CustomCanvasUserAgent();
    }


}
