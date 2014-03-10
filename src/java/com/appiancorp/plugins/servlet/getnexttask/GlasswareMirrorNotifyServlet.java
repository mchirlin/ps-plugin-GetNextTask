package com.appian.googleglass.mirror;

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

import javax.activation.DataSource;
import javax.mail.util.ByteArrayDataSource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.MultiPartEmail;
import org.apache.commons.mail.SimpleEmail;
import org.apache.log4j.Logger;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.mirror.Mirror;
import com.google.api.services.mirror.model.Attachment;
import com.google.api.services.mirror.model.MenuItem;
import com.google.api.services.mirror.model.Notification;
import com.google.api.services.mirror.model.TimelineItem;
import com.google.api.services.mirror.model.UserAction;

/**
 * Handled the notifications sent back from subscriptions
 *
 * @author jorge.sanchez
 */
public class GlasswareMirrorNotifyServlet extends HttpServlet {

  private static final long serialVersionUID = 9169350625721783901L;
  private static final Logger LOG = Logger.getLogger(GlasswareMirrorNotifyServlet.class);

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
    out.println("<br/><h1>Mirror Notify Servlet is up!</h1>");
    out.println("</body></html>");
    res.setContentType("text/html");
    out.close();
    return;
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
    // Respond with OK and status 200 in a timely fashion to prevent redelivery
    response.setContentType("text/html");
    Writer writer = response.getWriter();
    writer.append("OK");
    writer.close();

    // Get the notification object from the request body (into a string so we
    // can log it)
    BufferedReader notificationReader = new BufferedReader(new InputStreamReader(
      request.getInputStream()));
    String notificationString = "";

    // Count the lines as a very basic way to prevent Denial of Service attacks
    int lines = 0;
    String line;
    while ((line = notificationReader.readLine()) != null) {
      notificationString += line;
      lines++;
      // No notification would ever be this long. Something is very wrong.
      if (lines > 1000) {
        throw new IOException("Attempted to parse notification payload that was unexpectedly long.");
      }
    }
    LOG.info("got raw notification " + notificationString);
    JsonFactory jsonFactory = new JacksonFactory();
    // If logging the payload is not as important, use
    // jacksonFactory.fromInputStream instead.
    Notification notification = jsonFactory.fromString(notificationString, Notification.class);
    LOG.info("Got a notification with ID: " + notification.getItemId());

    // Figure out the impacted user and get their credentials for API calls
    String userId = notification.getUserToken();
    Credential credential = AuthUtil.getCredential(userId);
    Mirror mirrorClient = MirrorClient.getMirror(credential);

    if (notification.getCollection().equals("timeline")) {
      InputStream authPropertiesStream = AppianGlasswareUtils.resource.openStream();
      Properties glasswareProperties = new Properties();
      // Get the impacted timeline item
      TimelineItem timelineItem = mirrorClient.timeline().get(notification.getItemId()).execute();
      LOG.info("Notification impacted timeline item with ID: " + timelineItem.getId());

      // If it was a share, and contains a photo, update the photo's caption to
      // acknowledge that we got it.
      if (notification.getUserActions().contains(new UserAction().setType("SHARE")) &&
        timelineItem.getAttachments() != null && timelineItem.getAttachments().size() > 0) {
        LOG.info("It was a share of a photo. Sending to Appian Process to handle.");

        String caption = timelineItem.getText();
        if (caption == null) {
          caption = "";
        }
        Attachment sharedPhoto = timelineItem.getAttachments().get(0);
        InputStream photoStream = downloadAttachment(mirrorClient, sharedPhoto);
        LOG.info("Photo stream retrieved. Creating email with Attachment");
        try {
          authPropertiesStream = AppianGlasswareUtils.resource.openStream();
          glasswareProperties = new Properties();
          glasswareProperties.load(authPropertiesStream);
          sendMailWithAttachmentToProcess(photoStream, sharedPhoto.getContentType(),
            glasswareProperties, userId);
        } catch (EmailException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        // Create a new item with just the values that we want to patch.
        TimelineItem itemPatch = new TimelineItem();
        itemPatch.setText("Appian glassware got your photo! " + caption);

        mirrorClient.timeline().patch(notification.getItemId(), itemPatch).execute();
      } else if (notification.getUserActions().contains(new UserAction().setType("REPLY"))) {
        LOG.info("User replied to the timeline. Capturing the reply transcription and forwarding to"
          + " appian webservice to handle");
        String feedEventEntryId = timelineItem.getBundleId();
        String userComments = timelineItem.getText();
        /**
         * Fwd the data via email to a process model to handle replying to feed event.
         * This logic assumes that the id of the timeline event was set to that of the
         * target Feed event. Furthermore, this code should probably be updated to call a
         * Web Service instead. I just did this due to time constraints.
         */
        try {
          authPropertiesStream = AppianGlasswareUtils.resource.openStream();
          glasswareProperties = new Properties();
          glasswareProperties.load(authPropertiesStream);
          sendMailToProcess(glasswareProperties, feedEventEntryId, userComments, userId);
        } catch (EmailException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        LOG.info("Information sent to Appian. Updating the TimelineItem to indicate this.");
        timelineItem.setHtml(makeHtmlForCard("<p class='text-auto-size'>" + timelineItem.getText() +
          "</p>"));
        timelineItem.setText(null);
        timelineItem.setMenuItems(Collections.singletonList(new MenuItem().setAction("DELETE")));
        mirrorClient.timeline().update(timelineItem.getId(), timelineItem).execute();

      } else {
        LOG.debug("I don't know what to do with this notification, so I'm ignoring it.");
      }
    }
  }

  private void sendMailWithAttachmentToProcess(InputStream attachment, String contentType,
    Properties glasswareProperties, String userId) throws EmailException, IOException {
    MultiPartEmail email = new MultiPartEmail();
    email.setHostName(glasswareProperties.getProperty("email_host"));
    email.setSmtpPort(Integer.parseInt(glasswareProperties.getProperty("email_port")));
    String username = glasswareProperties.getProperty("email_user");
    if (username.length() > 0) {
      email.setAuthenticator(new DefaultAuthenticator(username,
        glasswareProperties.getProperty("email_pwd")));
    }
    email.setSSLOnConnect(Boolean.parseBoolean(glasswareProperties.getProperty("email_useSSL")));
    email.setCharset("utf-8");
    email.addTo(glasswareProperties.getProperty("email_targetAddress"));
    email.addCc(glasswareProperties.getProperty("email_cc"));
    email.setFrom(glasswareProperties.getProperty("email_from"));
    email.setSubject("Process Google Glass Share Notification");
    StringBuffer emailMsg = new StringBuffer();
    emailMsg.append("[appianUserId=" + AppianGlasswareUtils.getAppianUserForUserId(userId) + "]");
    emailMsg.append("[DestinationPMUUID=" +
      glasswareProperties.getProperty("share_target_pm.uuid") + "]");
    email.setMsg(emailMsg.toString());
    DataSource source = new ByteArrayDataSource(attachment, contentType);
    email.attach(source, "glassImageShare.jpeg", "A picture shared from google glass");
    email.send();
  }

  private InputStream downloadAttachment(Mirror service, Attachment attachment) {
    try {
      HttpResponse resp = service.getRequestFactory()
        .buildGetRequest(new GenericUrl(attachment.getContentUrl()))
        .execute();
      return resp.getContent();
    } catch (IOException e) {
      // An error occurred.
      LOG.error("There was an error getting the shared photo", e);
      return null;
    }
  }

  private void sendMailToProcess(Properties glasswareProperties, String feedEventEntryId,
    String userComments, String userId) throws EmailException {
    Email email = new SimpleEmail();
    email.setHostName(glasswareProperties.getProperty("email_host"));
    email.setSmtpPort(Integer.parseInt(glasswareProperties.getProperty("email_port")));
    String username = glasswareProperties.getProperty("email_user");
    if (username.length() > 0) {
      email.setAuthenticator(new DefaultAuthenticator(username,
        glasswareProperties.getProperty("email_pwd")));
    }
    email.setSSLOnConnect(Boolean.parseBoolean(glasswareProperties.getProperty("email_useSSL")));
    email.setCharset("utf-8");
    email.addTo(glasswareProperties.getProperty("email_targetAddress"));
    email.addCc(glasswareProperties.getProperty("email_cc"));
    email.setFrom(glasswareProperties.getProperty("email_from"));
    email.setSubject("Process Google Glass Reply Notification");
    StringBuffer emailMsg = new StringBuffer();
    emailMsg.append("[feedEventEntryId=" + feedEventEntryId + "]");
    emailMsg.append("[userComments=" + userComments + "]");
    emailMsg.append("[appianUserId=" + AppianGlasswareUtils.getAppianUserForUserId(userId) + "]");
    emailMsg.append("[DestinationPMUUID=" +
      glasswareProperties.getProperty("reply_target_pm.uuid") + "]");
    email.setMsg(emailMsg.toString());
    email.send();
  }

  /**
   * Wraps some HTML content in article/section tags and adds a footer identifying the
   * card as originating from the Java Quick Start.
   *
   * @param content
   *          the HTML content to wrap
   * @return the wrapped HTML content
   */
  private static String makeHtmlForCard(String content) {
    return "<article class='auto-paginate'>" + content +
      "<footer><p>Your comment was successfully posted to Appian.</p></footer></article>";
  }

}
