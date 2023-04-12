package io.harness.utils.featureflaghelper;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Collaborator;
import org.zendesk.client.v2.model.Comment;
import org.zendesk.client.v2.model.Ticket;

@Slf4j
public class ZendeskTicketHelper {

  private final Zendesk zd;

  public ZendeskTicketHelper() {
    zd = new Zendesk.Builder("https://hrnhelp.zendesk.com/")
        .setUsername("prateek.bit2006@yahoo.com")
        .setToken("bCYurjDabdslleiFMXi5oyku2C9f4aLtS5QKxotA")
        .build();
  }

  public Ticket getZendeskTicket(long ticketId) {
    return zd.getTicket(ticketId);
  }

  public Ticket addCommentToTicket(String commentData, long ticketId, boolean isPublicComment) {
    Comment cmt = new Comment();
    cmt.setBody(commentData);
    cmt.setPublic(isPublicComment);
    return zd.createComment(ticketId, cmt);
  }

  // call this in finally block, closes the zendesk connection created in constructor
  public void close() {
    zd.close();
  }

  // to be completed
  public void createTicket(String requesterName, String requesterEmail, Collaborator[] collaborators, List<String> tags, String ticketTitle, String ticketContent) {
    final Ticket ticket = new Ticket(
        new Ticket.Requester(requesterName, requesterEmail),
        ticketTitle + " " + UUID.randomUUID().toString(),
        new Comment("test content" + ticketContent));
    ticket.setCollaborators(Arrays.asList(collaborators));
    ticket.setTags(tags);

  }
}
