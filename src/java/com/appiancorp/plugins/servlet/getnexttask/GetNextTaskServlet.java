package com.appiancorp.plugins.servlet.getnexttask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javax.servlet.ServletException;


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
  protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException,
    IOException {

    return;
  }
  
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    
  }  

}
