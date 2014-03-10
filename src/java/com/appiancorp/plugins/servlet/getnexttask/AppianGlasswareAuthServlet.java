package com.appian.googleglass.mirror.auth;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

public class AppianGlasswareAuthServlet extends HttpServlet {

  private static final long serialVersionUID = 7501579750009610305L;
  private static final Logger LOG = Logger.getLogger(AppianGlasswareAuthServlet.class);

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException,
    IOException {
    PrintWriter out = new PrintWriter(res.getOutputStream());
    out.println("<!doctype html>");
    out.println("<html><body>");
    Map<String, String[]> params = req.getParameterMap();
    for (String key : params.keySet()) {
      out.println("<div>");
      out.println(key + " = ");
      String[] vals = params.get(key);
      if (vals.length == 1) {
        out.println(vals[0]);
      } else {
        out.println(Arrays.asList(vals));
      }
      out.println("</div>");
    }
    out.println("<br/><h1>Successfully Registered User credentials!</h1>");
    out.println("</body></html>");
    res.setContentType("text/html");
    out.close();
    return;
  }
}
