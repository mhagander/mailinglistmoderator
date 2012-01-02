Mailinglist Moderator
=====================

This is just a small program to make moderating mailinglists while on the road
a bit easier. If you're like me and don't like them to clutter up your mailbox,
at least.

The program will simply enumerate all pending moderation requests and let you
approve or reject them.

At this point, only posts are moderated, not subscription requests.

Installing
----------
Note! You are advised to install the program from the Android Market where
it's available for free. There are some APKs available for download from
the github page, but all updates are not always published there. The source
always is, of course, just not the prebuilt APKs.

Setting up a list
-----------------
To add a new mailinglist, hit the *Menu* key and select *Servers...*. Hit
*Menu* again, and select *New server*. In the window that shows up, enter the
*name of the mailinglist*. (Yes, there is some confusion of terms here, but this
is the name of the list, not the server).

When you've done this, you will be sent back to the main screen, and your list
will show up as unconfigured (in a future improved version, you should end up
directly at the list configuration page here). To configure the details, hit
the *Menu* key, and select *Servers* (again). Now click on the new server to
go into the configuration page for it, where you can enter the base URL and
the password.

If you have one or more existing servers, you can easily duplicate them by
clicking and holding on the name of the server and choose copy. This will copy
the base url and password, but give you a chance to enter a new name for the
list. This is particularly useful if you have a "site-wide" administration
password that will grant you access to all lists.

Base URLs
---------
The *base url* should be set to the root of the list server management URL.
It will be different depending on which list manager is used. Note that the
base url does *not* include the name of the list.

Mailman
+++++++
For mailman, the base url is typically ``http://lists.domain.com/mailman/admindb``.

Majordomo2
++++++++++
For majordomo2, the base url is typically ``http://lists.domain.com/mj/mj_wwwadm``.

Moderating
----------
When the application starts it will enumerate all unmoderated emails on all the
lists, and the main view will show how many unmoderated messages there are on the
list. The entries will be sorted so that the list with the largest amount of
pending moderation requests is listed on top.

To moderate the entries on a list, just click the list from the main view. This
will give a list of all the emails pending on this list, the sender of them
and the subject. To view the contents of the mail, just click the mail.

To moderate an individual email, click-hold on it and choose to reject or approve
it. You can also hit the *Menu* key and from the popup menu choose to reject
or approve all messages at once.

Once you're satisfied with the status of the messages, hit the *Menu* key and
select *Apply moderation*. This will call out to the server and make the actual
moderation changes.

Depending on mailinglist manager this will be a single operation (for mailman)
or a sequence of one operation for each message (majordomo2), and may in the
majordomo2 case take a while if it's a lot of emails to be moderated.

Common issues
-------------

* The mailinglist software to be in English - localized versions don't work.
