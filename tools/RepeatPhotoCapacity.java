import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/** Local-only capacity fixture. Duplicates the authorized file; does not access the server. */
public class RepeatPhotoCapacity {
    static Document xml(ZipFile zip, String name) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream input = zip.getInputStream(zip.getEntry(name))) { return factory.newDocumentBuilder().parse(input); }
    }
    static byte[] bytes(Document document) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.transform(new DOMSource(document), new StreamResult(out)); return out.toByteArray();
    }
    static List<Element> children(Element element) {
        List<Element> result = new ArrayList<>();
        for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element) result.add((Element)n);
        return result;
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Path output = root.resolve("single-300-rebuilt.xlsx");
        if (Files.exists(output)) throw new IOException("Test target already exists");
        try (ZipFile base = new ZipFile(root.resolve("single-150.xlsx").toFile());
             ZipOutputStream dest = new ZipOutputStream(Files.newOutputStream(output, StandardOpenOption.CREATE_NEW))) {
            Document sheet = xml(base, "xl/worksheets/sheet1.xml");
            Element data = (Element)sheet.getElementsByTagNameNS("*", "sheetData").item(0);
            List<Element> rows = children(data);
            int offset = rows.size() - 1;
            for (Element row : rows) {
                if (row.getAttribute("r").equals("1")) continue;
                Element clone = (Element)row.cloneNode(true);
                clone.setAttribute("r", "" + (Integer.parseInt(row.getAttribute("r")) + offset));
                for (Element cell : children(clone)) {
                    String ref = cell.getAttribute("r");
                    cell.setAttribute("r", ref.replaceAll("[0-9]+$", clone.getAttribute("r")));
                    if (ref.matches("A[0-9]+")) cell.getElementsByTagNameNS("*", "t").item(0).setTextContent("" +
                        (Integer.parseInt(cell.getElementsByTagNameNS("*", "t").item(0).getTextContent()) + 150));
                    if (ref.matches("B[0-9]+")) cell.getElementsByTagNameNS("*", "t").item(0).setTextContent("容量模拟_重复样本");
                }
                data.appendChild(clone);
            }
            Element filter = (Element)sheet.getElementsByTagNameNS("*", "autoFilter").item(0);
            filter.setAttribute("ref", filter.getAttribute("ref").replaceAll("[0-9]+$", "" + (offset * 2 + 1)));
            Document drawing = xml(base, "xl/drawings/drawing1.xml");
            for (Element anchor : children(drawing.getDocumentElement())) {
                Element clone = (Element)anchor.cloneNode(true);
                NodeList anchorRows = clone.getElementsByTagNameNS("*", "row");
                for (int i = 0; i < anchorRows.getLength(); i++) anchorRows.item(i).setTextContent("" +
                    (Integer.parseInt(anchorRows.item(i).getTextContent()) + offset));
                Element props = (Element)clone.getElementsByTagNameNS("*", "cNvPr").item(0);
                props.setAttribute("id", "" + (Integer.parseInt(props.getAttribute("id")) + 735));
                props.setAttribute("name", "容量模拟照片" + props.getAttribute("id"));
                Element blip = (Element)clone.getElementsByTagNameNS("*", "blip").item(0);
                blip.setAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "r:embed", "rId" + props.getAttribute("id"));
                drawing.getDocumentElement().appendChild(clone);
            }
            Document rels = xml(base, "xl/drawings/_rels/drawing1.xml.rels");
            for (Element rel : children(rels.getDocumentElement())) {
                Element clone = (Element)rel.cloneNode(true);
                int number = Integer.parseInt(rel.getAttribute("Id").substring(3)) + 735;
                clone.setAttribute("Id", "rId" + number);
                clone.setAttribute("Target", "../media/image" + number + ".jpg");
                rels.getDocumentElement().appendChild(clone);
            }
            Map<String, byte[]> replaced = Map.of("xl/worksheets/sheet1.xml", bytes(sheet),
                "xl/drawings/drawing1.xml", bytes(drawing), "xl/drawings/_rels/drawing1.xml.rels", bytes(rels));
            for (ZipEntry e : Collections.list(base.entries())) {
                if (e.getName().startsWith("xl/media/")) continue;
                dest.putNextEntry(new ZipEntry(e.getName()));
                if (replaced.containsKey(e.getName())) dest.write(replaced.get(e.getName()));
                else try (InputStream input = base.getInputStream(e)) { input.transferTo(dest); }
                dest.closeEntry();
            }
            for (int i = 1; i <= 1470; i++) {
                ZipEntry source = base.getEntry("xl/media/image" + ((i - 1) % 735 + 1) + ".jpg");
                if (source == null) throw new IOException("Source image missing");
                ZipEntry target = new ZipEntry("xl/media/image" + i + ".jpg");
                target.setMethod(ZipEntry.STORED);
                target.setSize(source.getSize()); target.setCompressedSize(source.getSize()); target.setCrc(source.getCrc());
                dest.putNextEntry(target);
                try (InputStream input = base.getInputStream(source)) { input.transferTo(dest); }
                dest.closeEntry();
            }
        }
        System.out.println("Local repeated-photo test fixture: " + Files.size(output) + " bytes");
    }
}
