package com.lxl;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Created by 李晓林 on 2017/2/15
 * qq:1220289215
 */

public class XmlParseDemo {
    public static void main(String[] args) {
        try {
            FileInputStream fis = new FileInputStream("");
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(fis);
            Element documentElement = document.getDocumentElement();
            NodeList nodeList = documentElement.getElementsByTagName("string");
            int length = nodeList.getLength();
            for (int i = length-1; i >=0 ; i--) {
                Element item = (Element) nodeList.item(i);
                String product = item.getAttribute("product");

                if ("tablet".equals(product)) {
                    item.getParentNode().removeChild(item);
                }
            }
            TransformerFactory tfactory = TransformerFactory.newInstance();
            Transformer transformer = tfactory.newTransformer();
            DOMSource source = new DOMSource(document);

            File file = new File(
                    "C:\\Users\\Administrator\\Desktop\\strings.xml");
            StreamResult result = new StreamResult(file);
            transformer.transform(source, result);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }
}
