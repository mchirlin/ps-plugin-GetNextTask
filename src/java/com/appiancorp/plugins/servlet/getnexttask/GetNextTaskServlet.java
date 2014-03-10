package com.appiancorp.plugins.servlet.getnexttask;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * Handled the notifications sent back from subscriptions
 *
 * @author michael.chirlin
 */
public class GetNextTaskServlet extends HttpServlet {

  private static final long serialVersionUID = 9169350625721783901L;
  private static final Logger LOG = Logger.getLogger(GetNextTaskServlet.class);

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException {
	  
  }
  
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException  {
	  
  }
}
