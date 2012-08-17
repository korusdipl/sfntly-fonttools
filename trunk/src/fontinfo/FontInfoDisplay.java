package fontinfo;

import com.google.typography.font.sfntly.Font;
import com.google.typography.font.sfntly.FontFactory;
import com.google.typography.font.sfntly.Tag;
import com.google.typography.font.sfntly.Font.PlatformId;
import com.google.typography.font.sfntly.Font.WindowsEncodingId;
import com.google.typography.font.sfntly.table.core.NameTable;
import com.google.typography.font.sfntly.table.core.NameTable.NameId;
import com.google.typography.font.sfntly.table.core.NameTable.WindowsLanguageId;
import com.google.typography.font.tools.fontinfo.CommandOptions;
import com.google.typography.font.tools.fontinfo.DataDisplayTable;
import com.google.typography.font.tools.fontinfo.FontInfo;
import com.google.typography.font.tools.fontinfo.FontUtils;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class FontInfoDisplay extends HttpServlet {
  private static final Logger LOGGER = Logger.getLogger(FontInfoDisplay.class.getName());
  private static final String FONT_FILE_FIELD = "fontFile";
  private static final String DISP_OPT_NAME = "displayOption";
  private static final String OPT_GENERAL = "general";
  private static final String OPT_METRICS = "metrics";
  private static final String OPT_CMAPS = "cmaps";
  private static final String OPT_BLOCKS = "blocks";
  private static final String OPT_SCRIPTS = "scripts";
  private static final String OPT_CHARS = "char";
  private static final String OPT_SHOW_CHARS = "showChars";
  private static final String OPT_GLYPHS = "glyphs";

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException {
    try {
      res.setContentType("text/html");

      StringBuilder output = new StringBuilder("<html><body>");
      output.append("<form action=\"./fontinfo\" method=\"post\" enctype=\"multipart/form-data\">")
          .append("Upload file: <input type=\"file\" name=\"" + FONT_FILE_FIELD + "\" /><br />")
          .append("Options<br />")
          .append("<input type=\"checkbox\" name=\"" + DISP_OPT_NAME + "\" value=\"" + OPT_GENERAL
              + "\"> General<br />")
          .append("<input type=\"checkbox\" name=\"" + DISP_OPT_NAME + "\" value=\"" + OPT_METRICS
              + "\"> Font metrics<br />")
          .append("<input type=\"checkbox\" name=\"" + DISP_OPT_NAME + "\" value=\"" + OPT_CMAPS
              + "\"> Cmap information<br />")
          .append("<input type=\"checkbox\" name=\"" + DISP_OPT_NAME + "\" value=\"" + OPT_BLOCKS
              + "\"> Unicode block coverage<br />")
          .append("<input type=\"checkbox\" name=\"" + DISP_OPT_NAME + "\" value=\"" + OPT_SCRIPTS
              + "\"> Script coverage<br />")
          .append("<input type=\"checkbox\" name=\"" + DISP_OPT_NAME + "\" value=\"" + OPT_CHARS
              + "\"> Character information<br />")
          .append("&emsp;&emsp;<input type=\"checkbox\" name=\"" + DISP_OPT_NAME + "\" value=\""
              + OPT_SHOW_CHARS + "\"> Display characters using the font in character list<br />")
          .append("<input type=\"checkbox\" name=\"" + DISP_OPT_NAME + "\" value=\"" + OPT_GLYPHS
              + "\"> Glyph information<br />")
          .append(
              "<br /><input type=\"submit\" value=\"Show information\" /></form></body></html>");
      res.getOutputStream().println(output.toString());
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException {
    try {
      ServletFileUpload upload = new ServletFileUpload();
      res.setContentType("text/html");
      PrintWriter writer = res.getWriter();
      Font font = null;
      CommandOptions options = new CommandOptions();
      boolean showChars = false;
      StringBuilder headItems = new StringBuilder(
          "<meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\">");
      String fontName = "";

      // Iterate through form fields to retrieve font file and other information
      FileItemIterator iterator = upload.getItemIterator(req);
      while (iterator.hasNext()) {
        FileItemStream item = iterator.next();
        InputStream stream = item.openStream();

        if (item.isFormField() && item.getFieldName() != null
            && item.getFieldName().equals(DISP_OPT_NAME)) {
          // An option was selected
          String option = Streams.asString(stream);

          if (option.equals(OPT_GENERAL)) {
            LOGGER.info(OPT_GENERAL);
            options.general = true;
          }
          if (option.equals(OPT_METRICS)) {
            LOGGER.info(OPT_METRICS);
            options.metrics = true;
          }
          if (option.equals(OPT_CMAPS)) {
            LOGGER.info(OPT_CMAPS);
            options.cmap = true;
          }
          if (option.equals(OPT_BLOCKS)) {
            LOGGER.info(OPT_BLOCKS);
            options.blocks = true;
          }
          if (option.equals(OPT_SCRIPTS)) {
            LOGGER.info(OPT_SCRIPTS);
            options.scripts = true;
          }
          if (option.equals(OPT_CHARS)) {
            LOGGER.info(OPT_CHARS);
            options.chars = true;
          }
          if (option.equals(OPT_SHOW_CHARS)) {
            LOGGER.info(OPT_SHOW_CHARS);
            showChars = true;
          }
          if (option.equals(OPT_GLYPHS)) {
            LOGGER.info(OPT_GLYPHS);
            options.glyphs = true;
          }
        }

        if (item.getFieldName().equals(FONT_FILE_FIELD)) {
          try {
            // TODO Provide support for TTCs
            font = FontUtils.getFonts(stream)[0];
            fontName = item.getName();
          } catch (Error e) {
            LOGGER.warning("Upload of invalid file attempted");
            writer.println(item.getName() + " is not a valid TrueType font file.");
            return;
          }
        }
      }

      if (showChars) {
        webFontHeader(serialiseFont(font), headItems);
      }

      writer.println("<html><head>");
      writer.println(headItems.toString());
      writer.println("</head><body>");
      writer.println("<pre>");
      
      writer.println("Font file: " + fontName);
//      writer.println("Font version: " + getFontVersion(font));
      writer.println();

      outputFontData(font, res, options, showChars);

      writer.println("</pre>");
      writer.println("</body></html>");

    } catch (Exception ex) {
      throw new ServletException(ex);
    }
  }

  private void outputFontData(
      Font font, HttpServletResponse res, CommandOptions options, boolean showChars)
      throws IOException {
    PrintWriter writer = res.getWriter();

    if (options.general) {
      writer.println(String.format("sfnt version: %s", FontInfo.sfntVersion(font)));
      writer.println();
      writer.println("Font Tables:");
      writer.println(FontInfo.listTables(font).prettyString());
      writer.println();
      // writer.println(FontInfo.listNameEntries(font).prettyString());
      // writer.println();
    }

    if (options.metrics) {
      writer.println("Font Metrics:");
      writer.println(FontInfo.listFontMetrics(font).prettyString());
      writer.println();
    }

    if (options.metrics || options.glyphs) {
      writer.println("Glyph Metrics:");
      writer.println(FontInfo.listGlyphDimensionBounds(font).prettyString());
      writer.println();
    }

    if (options.cmap) {
      writer.println("Cmaps in the font:");
      writer.println(FontInfo.listCmaps(font).prettyString());
      writer.println();
    }

    if (options.blocks) {
      writer.println("Unicode block coverage:");
      writer.println(FontInfo.listCharBlockCoverage(font).prettyString());
      writer.println();
    }

    if (options.scripts) {
      writer.println("Unicode script coverage:");
      writer.println(FontInfo.listScriptCoverage(font).prettyString());
      writer.println();
    }

    if (options.chars) {
      writer.println((String.format(
          "Total number of characters with valid glyphs: %d", FontInfo.numChars(font))));
      writer.println();
      if (showChars) {
        writer.println("</pre>");
        displayCharsWithCharList(font, writer);
        writer.println("<pre>");
      } else {
        writer.println("Characters with valid glyphs:");
        writer.println(FontInfo.listChars(font).prettyString());
        writer.println();
      }
    }

    if (options.glyphs) {
      DataDisplayTable unmappedGlyphs = FontInfo.listUnmappedGlyphs(font);

      writer.println(String.format("Total hinting size: %s", FontInfo.hintingSize(font)));
      writer.println();
      writer.println(String.format(
          "Number of unmapped glyphs: %d / %d", unmappedGlyphs.getNumRows(),
          FontInfo.numGlyphs(font)));
      writer.println();
      writer.println("Unmapped glyphs:");
      writer.println(FontInfo.listUnmappedGlyphs(font).prettyString());
      writer.println();
      writer.println("Subglyphs used by characters in the font:");
      writer.println(FontInfo.listSubglyphFrequency(font).prettyString());
      writer.println();
    }
  }

  private String getFontVersion(Font font) {
    NameTable nameTable = (NameTable) FontUtils.getTable(font, Tag.name);
    String value = nameTable.name(PlatformId.Windows.value(), WindowsEncodingId.UnicodeUCS2.value(),
        WindowsLanguageId.English_UnitedStates.value(), NameId.VersionString.value());
    return (value == null ? "Unknown" : value);
  }

  private byte[] serialiseFont(Font font) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    byte[] fileArray = null;
    try {
      FontFactory factory = FontFactory.getInstance();
      factory.serializeFont(font, os);
      fileArray = os.toByteArray();
    } finally {
      os.close();
    }

    return fileArray;
  }

  private void webFontHeader(byte[] fontArray, StringBuilder headStr) {
    if (headStr == null) {
      headStr = new StringBuilder();
    }

    headStr.append("<style type=\"text/css\" media=\"screen\">");
    headStr.append("@font-face{font-family:'SpecialFont';src:url(\"data:font/opentype;base64,");

    headStr.append(Base64.encodeBase64String(fontArray));

    headStr.append("\");} .webfont{font-family:'SpecialFont', Serif;}</style>");
  }

  private void displayCharsWithCharList(Font font, PrintWriter writer) {
    DataDisplayTable charInfoList = FontInfo.listChars(font);
    StringBuilder output = new StringBuilder("<br />Characters with valid glyphs:<br />");
    // Header
    output.append("<table><tr>");
    for (String item : charInfoList.getHeader()) {
      output.append("<th>");
      output.append(item);
      output.append("</th>");
    }
    output.append("<th>Character</th></tr>");

    // Data
    for (List<String> row : charInfoList.getData()) {
      output.append("<tr>");

      // TODO temp
      boolean first = true;
      String unicodeChar = "";

      for (String item : row) {
        if (first) {
          first = false;
          unicodeChar = item.replace("U+", "&#x");
        }
        output.append("<td>");
        output.append(item);
        output.append("</td>");
      }
      output.append("<td class=\"webfont\">");
      output.append("&nbsp;" + unicodeChar + ";");
      output.append("</td>");
      output.append("</tr>\n");
    }

    output.append("</table>");
    writer.println(output.toString());
  }
}
