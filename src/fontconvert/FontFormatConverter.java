package fontconvert;

import com.google.typography.font.sfntly.Font;
import com.google.typography.font.sfntly.FontFactory;
import com.google.typography.font.sfntly.Tag;
import com.google.typography.font.sfntly.data.WritableFontData;
import com.google.typography.font.tools.conversion.eot.EOTWriter;
import com.google.typography.font.tools.conversion.woff.WoffWriter;
import com.google.typography.font.tools.fontinfo.FontUtils;
import com.google.typography.font.tools.subsetter.HintStripper;
import com.google.typography.font.tools.subsetter.Subsetter;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class FontFormatConverter extends HttpServlet {
  private static final Logger LOGGER = Logger.getLogger(FontFormatConverter.class.getName());
  private static final String FONT_FILE_FIELD = "fontFile";
  private static final String OPT_STRIP = "strip";
  private static final String OPT_FORMAT = "format";
  private static final String OPT_EOT = "eot";
  private static final String OPT_WOFF = "woff";

  public enum FontFormat {
    Undef, Eot, Woff
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException {
    try {
      StringBuilder output = new StringBuilder("<html><body>");
      output.append(
          "<form action=\"./fontconvert\" method=\"post\" enctype=\"multipart/form-data\">")
          .append("Upload file: <input type=\"file\" name=\"" + FONT_FILE_FIELD + "\" /><br />")
          .append("<input type=\"checkbox\" name=\"" + OPT_STRIP + "\"> Strip hinting<br />")
          .append("<input type=\"radio\" name=\"" + OPT_FORMAT + "\" value=\"" + OPT_EOT
              + "\"> EOT<br />")
          .append("<input type=\"radio\" name=\"" + OPT_FORMAT + "\" value=\"" + OPT_WOFF
              + "\"> WOFF<br />")
          .append("<br /><input type=\"submit\" value=\"Convert\" /></form></body></html>");
      res.getOutputStream().println(output.toString());
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
  }

  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException {
    try {
      ServletFileUpload upload = new ServletFileUpload();
      Font font = null;
      boolean strip = false;
      FontFormat format = FontFormat.Undef;
      String fileName = "";

      // Iterate through form fields to retrieve font file and other information
      FileItemIterator iterator = upload.getItemIterator(req);
      while (iterator.hasNext()) {
        FileItemStream item = iterator.next();
        InputStream stream = item.openStream();

        // Strip hinting
        if (item.isFormField() && item.getFieldName() != null
            && item.getFieldName().equals(OPT_STRIP)) {
          LOGGER.info(OPT_STRIP);
          strip = true;
        }

        // Format selection
        if (item.isFormField() && item.getFieldName() != null
            && item.getFieldName().equals(OPT_FORMAT)) {
          String option = Streams.asString(stream);

          if (option.equals(OPT_EOT)) {
            LOGGER.info(OPT_EOT);
            format = FontFormat.Eot;
          }
          if (option.equals(OPT_WOFF)) {
            LOGGER.info(OPT_WOFF);
            format = FontFormat.Woff;
          }
        }

        // File uploaded
        if (item.getFieldName().equals(FONT_FILE_FIELD)) {
          try {
            // TODO Provide support for TTCs
            font = FontUtils.getFonts(stream)[0];
          } catch (Error e) {
            LOGGER.warning("Upload of invalid file attempted");
            outputError(item.getName() + " is not a valid TrueType font file.", res);
            return;
          }
          fileName = FilenameUtils.removeExtension(item.getName());
        }
      }

      if (format == FontFormat.Undef) {
        outputError("Please choose an output format.", res);
        return;
      }

      convertFont(font, fileName, format, strip, res);
    } catch (Exception ex) {
      throw new ServletException(ex);
    }
  }

  private void convertFont(
      Font font, String fileName, FontFormat format, boolean strip, HttpServletResponse res)
      throws IOException {
    Font newFont = font;

    // TODO
    boolean mtx = false;

    // Strip hinting
    if (strip) {
      FontFactory fontFactory = FontFactory.getInstance();
      Subsetter hintStripper = new HintStripper(newFont, fontFactory);
      Set<Integer> removeTables = new HashSet<Integer>();
      removeTables.add(Tag.fpgm);
      removeTables.add(Tag.prep);
      removeTables.add(Tag.cvt);
      removeTables.add(Tag.hdmx);
      removeTables.add(Tag.VDMX);
      removeTables.add(Tag.LTSH);
      removeTables.add(Tag.DSIG);
      hintStripper.setRemoveTables(removeTables);
      newFont = hintStripper.subset().build();
    }

    OutputStream os = res.getOutputStream();
    if (format == FontFormat.Eot) {
      res.setContentType("application/vnd.ms-fontobject");
      res.setHeader("Content-Disposition", "attachment; filename=" + fileName + ".eot");
      WritableFontData eotData = new EOTWriter(mtx).convert(newFont);
      eotData.copyTo(os);
    } else if (format == FontFormat.Woff) {
      res.setContentType("application/font-woff");
      res.setHeader("Content-Disposition", "attachment; filename=" + fileName + ".woff");
      WritableFontData woffData = new WoffWriter().convert(newFont);
      woffData.copyTo(os);
    }
  }

  private void outputError(String error, HttpServletResponse res) throws IOException {
    res.setContentType("text/plain");
    res.getWriter().println(error);
  }
}
