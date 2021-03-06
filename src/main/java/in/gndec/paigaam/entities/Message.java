package in.gndec.paigaam.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.text.SpannableStringBuilder;

import java.net.MalformedURLException;
import java.net.URL;

import in.gndec.paigaam.Config;
import in.gndec.paigaam.crypto.axolotl.FingerprintStatus;
import in.gndec.paigaam.http.AesGcmURLStreamHandler;
import in.gndec.paigaam.ui.adapter.MessageAdapter;
import in.gndec.paigaam.utils.CryptoHelper;
import in.gndec.paigaam.utils.Emoticons;
import in.gndec.paigaam.utils.GeoHelper;
import in.gndec.paigaam.utils.MimeUtils;
import in.gndec.paigaam.utils.UIHelper;
import in.gndec.paigaam.xmpp.jid.InvalidJidException;
import in.gndec.paigaam.xmpp.jid.Jid;

public class Message extends AbstractEntity {

	public static final String TABLENAME = "messages";

	public static final int STATUS_RECEIVED = 0;
	public static final int STATUS_UNSEND = 1;
	public static final int STATUS_SEND = 2;
	public static final int STATUS_SEND_FAILED = 3;
	public static final int STATUS_WAITING = 5;
	public static final int STATUS_OFFERED = 6;
	public static final int STATUS_SEND_RECEIVED = 7;
	public static final int STATUS_SEND_DISPLAYED = 8;

	public static final int ENCRYPTION_NONE = 0;
	public static final int ENCRYPTION_PGP = 1;
	public static final int ENCRYPTION_OTR = 2;
	public static final int ENCRYPTION_DECRYPTED = 3;
	public static final int ENCRYPTION_DECRYPTION_FAILED = 4;
	public static final int ENCRYPTION_AXOLOTL = 5;

	public static final int TYPE_TEXT = 0;
	public static final int TYPE_IMAGE = 1;
	public static final int TYPE_FILE = 2;
	public static final int TYPE_STATUS = 3;
	public static final int TYPE_PRIVATE = 4;

	public static final String CONVERSATION = "conversationUuid";
	public static final String COUNTERPART = "counterpart";
	public static final String TRUE_COUNTERPART = "trueCounterpart";
	public static final String BODY = "body";
	public static final String TIME_SENT = "timeSent";
	public static final String ENCRYPTION = "encryption";
	public static final String STATUS = "status";
	public static final String TYPE = "type";
	public static final String CARBON = "carbon";
	public static final String OOB = "oob";
	public static final String EDITED = "edited";
	public static final String REMOTE_MSG_ID = "remoteMsgId";
	public static final String SERVER_MSG_ID = "serverMsgId";
	public static final String RELATIVE_FILE_PATH = "relativeFilePath";
	public static final String FINGERPRINT = "axolotl_fingerprint";
	public static final String READ = "read";
	public static final String ERROR_MESSAGE = "errorMsg";
	public static final String ME_COMMAND = "/me ";


	public boolean markable = false;
	protected String conversationUuid;
	protected Jid counterpart;
	protected Jid trueCounterpart;
	protected String body;
	protected String encryptedBody;
	protected long timeSent;
	protected int encryption;
	protected int status;
	protected int type;
	protected boolean carbon = false;
	protected boolean oob = false;
	protected String edited = null;
	protected String relativeFilePath;
	protected boolean read = true;
	protected String remoteMsgId = null;
	protected String serverMsgId = null;
	private final Conversation conversation;
	protected Transferable transferable = null;
	private Message mNextMessage = null;
	private Message mPreviousMessage = null;
	private String axolotlFingerprint = null;
	private String errorMessage = null;

	private Boolean isGeoUri = null;
	private Boolean isEmojisOnly = null;
	private Boolean treatAsDownloadable = null;
	private FileParams fileParams = null;

	private Message(Conversation conversation) {
		this.conversation = conversation;
	}

	public Message(Conversation conversation, String body, int encryption) {
		this(conversation, body, encryption, STATUS_UNSEND);
	}

	public Message(Conversation conversation, String body, int encryption, int status) {
		this(conversation, java.util.UUID.randomUUID().toString(),
				conversation.getUuid(),
				conversation.getJid() == null ? null : conversation.getJid().toBareJid(),
				null,
				body,
				System.currentTimeMillis(),
				encryption,
				status,
				TYPE_TEXT,
				false,
				null,
				null,
				null,
				null,
				true,
				null,
				false,
				null);
	}

	private Message(final Conversation conversation, final String uuid, final String conversationUUid, final Jid counterpart,
					final Jid trueCounterpart, final String body, final long timeSent,
					final int encryption, final int status, final int type, final boolean carbon,
					final String remoteMsgId, final String relativeFilePath,
					final String serverMsgId, final String fingerprint, final boolean read,
					final String edited, final boolean oob, final String errorMessage) {
		this.conversation = conversation;
		this.uuid = uuid;
		this.conversationUuid = conversationUUid;
		this.counterpart = counterpart;
		this.trueCounterpart = trueCounterpart;
		this.body = body == null ? "" : body;
		this.timeSent = timeSent;
		this.encryption = encryption;
		this.status = status;
		this.type = type;
		this.carbon = carbon;
		this.remoteMsgId = remoteMsgId;
		this.relativeFilePath = relativeFilePath;
		this.serverMsgId = serverMsgId;
		this.axolotlFingerprint = fingerprint;
		this.read = read;
		this.edited = edited;
		this.oob = oob;
		this.errorMessage = errorMessage;
	}

	public static Message fromCursor(Cursor cursor, Conversation conversation) {
		Jid jid;
		try {
			String value = cursor.getString(cursor.getColumnIndex(COUNTERPART));
			if (value != null) {
				jid = Jid.fromString(value, true);
			} else {
				jid = null;
			}
		} catch (InvalidJidException e) {
			jid = null;
		} catch (IllegalStateException e) {
			return null; // message too long?
		}
		Jid trueCounterpart;
		try {
			String value = cursor.getString(cursor.getColumnIndex(TRUE_COUNTERPART));
			if (value != null) {
				trueCounterpart = Jid.fromString(value, true);
			} else {
				trueCounterpart = null;
			}
		} catch (InvalidJidException e) {
			trueCounterpart = null;
		}
		return new Message(conversation,
				cursor.getString(cursor.getColumnIndex(UUID)),
				cursor.getString(cursor.getColumnIndex(CONVERSATION)),
				jid,
				trueCounterpart,
				cursor.getString(cursor.getColumnIndex(BODY)),
				cursor.getLong(cursor.getColumnIndex(TIME_SENT)),
				cursor.getInt(cursor.getColumnIndex(ENCRYPTION)),
				cursor.getInt(cursor.getColumnIndex(STATUS)),
				cursor.getInt(cursor.getColumnIndex(TYPE)),
				cursor.getInt(cursor.getColumnIndex(CARBON)) > 0,
				cursor.getString(cursor.getColumnIndex(REMOTE_MSG_ID)),
				cursor.getString(cursor.getColumnIndex(RELATIVE_FILE_PATH)),
				cursor.getString(cursor.getColumnIndex(SERVER_MSG_ID)),
				cursor.getString(cursor.getColumnIndex(FINGERPRINT)),
				cursor.getInt(cursor.getColumnIndex(READ)) > 0,
				cursor.getString(cursor.getColumnIndex(EDITED)),
				cursor.getInt(cursor.getColumnIndex(OOB)) > 0,
				cursor.getString(cursor.getColumnIndex(ERROR_MESSAGE)));
	}

	public static Message createStatusMessage(Conversation conversation, String body) {
		final Message message = new Message(conversation);
		message.setType(Message.TYPE_STATUS);
		message.setStatus(Message.STATUS_RECEIVED);
		message.body = body;
		return message;
	}

	public static Message createLoadMoreMessage(Conversation conversation) {
		final Message message = new Message(conversation);
		message.setType(Message.TYPE_STATUS);
		message.body = "LOAD_MORE";
		return message;
	}

	public static Message createDateSeparator(Message message) {
		final Message separator = new Message(message.getConversation());
		separator.setType(Message.TYPE_STATUS);
		separator.body = MessageAdapter.DATE_SEPARATOR_BODY;
		separator.setTime(message.getTimeSent());
		return separator;
	}

	@Override
	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(CONVERSATION, conversationUuid);
		if (counterpart == null) {
			values.putNull(COUNTERPART);
		} else {
			values.put(COUNTERPART, counterpart.toPreppedString());
		}
		if (trueCounterpart == null) {
			values.putNull(TRUE_COUNTERPART);
		} else {
			values.put(TRUE_COUNTERPART, trueCounterpart.toPreppedString());
		}
		values.put(BODY, body.length() > Config.MAX_STORAGE_MESSAGE_CHARS ? body.substring(0,Config.MAX_STORAGE_MESSAGE_CHARS) : body);
		values.put(TIME_SENT, timeSent);
		values.put(ENCRYPTION, encryption);
		values.put(STATUS, status);
		values.put(TYPE, type);
		values.put(CARBON, carbon ? 1 : 0);
		values.put(REMOTE_MSG_ID, remoteMsgId);
		values.put(RELATIVE_FILE_PATH, relativeFilePath);
		values.put(SERVER_MSG_ID, serverMsgId);
		values.put(FINGERPRINT, axolotlFingerprint);
		values.put(READ,read ? 1 : 0);
		values.put(EDITED, edited);
		values.put(OOB, oob ? 1 : 0);
		values.put(ERROR_MESSAGE,errorMessage);
		return values;
	}

	public String getConversationUuid() {
		return conversationUuid;
	}

	public Conversation getConversation() {
		return this.conversation;
	}

	public Jid getCounterpart() {
		return counterpart;
	}

	public void setCounterpart(final Jid counterpart) {
		this.counterpart = counterpart;
	}

	public Contact getContact() {
		if (this.conversation.getMode() == Conversation.MODE_SINGLE) {
			return this.conversation.getContact();
		} else {
			if (this.trueCounterpart == null) {
				return null;
			} else {
				return this.conversation.getAccount().getRoster()
						.getContactFromRoster(this.trueCounterpart);
			}
		}
	}

	public String getBody() {
		return body;
	}

	public synchronized void setBody(String body) {
		if (body == null) {
			throw new Error("You should not set the message body to null");
		}
		this.body = body;
		this.isGeoUri = null;
		this.isEmojisOnly = null;
		this.treatAsDownloadable = null;
		this.fileParams = null;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public boolean setErrorMessage(String message) {
		boolean changed = (message != null && !message.equals(errorMessage))
				|| (message == null && errorMessage != null);
		this.errorMessage = message;
		return changed;
	}

	public long getTimeSent() {
		return timeSent;
	}

	public int getEncryption() {
		return encryption;
	}

	public void setEncryption(int encryption) {
		this.encryption = encryption;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public String getRelativeFilePath() {
		return this.relativeFilePath;
	}

	public void setRelativeFilePath(String path) {
		this.relativeFilePath = path;
	}

	public String getRemoteMsgId() {
		return this.remoteMsgId;
	}

	public void setRemoteMsgId(String id) {
		this.remoteMsgId = id;
	}

	public String getServerMsgId() {
		return this.serverMsgId;
	}

	public void setServerMsgId(String id) {
		this.serverMsgId = id;
	}

	public boolean isRead() {
		return this.read;
	}

	public void markRead() {
		this.read = true;
	}

	public void markUnread() {
		this.read = false;
	}

	public void setTime(long time) {
		this.timeSent = time;
	}

	public String getEncryptedBody() {
		return this.encryptedBody;
	}

	public void setEncryptedBody(String body) {
		this.encryptedBody = body;
	}

	public int getType() {
		return this.type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public boolean isCarbon() {
		return carbon;
	}

	public void setCarbon(boolean carbon) {
		this.carbon = carbon;
	}

	public void setEdited(String edited) {
		this.edited = edited;
	}

	public boolean edited() {
		return this.edited != null;
	}

	public void setTrueCounterpart(Jid trueCounterpart) {
		this.trueCounterpart = trueCounterpart;
	}

	public Jid getTrueCounterpart() {
		return this.trueCounterpart;
	}

	public Transferable getTransferable() {
		return this.transferable;
	}

	public synchronized void setTransferable(Transferable transferable) {
		this.fileParams = null;
		this.transferable = transferable;
	}

	public boolean similar(Message message) {
		if (type != TYPE_PRIVATE && this.serverMsgId != null && message.getServerMsgId() != null) {
			return this.serverMsgId.equals(message.getServerMsgId());
		} else if (this.body == null || this.counterpart == null) {
			return false;
		} else {
			String body, otherBody;
			if (this.hasFileOnRemoteHost()) {
				body = getFileParams().url.toString();
				otherBody = message.body == null ? null : message.body.trim();
			} else {
				body = this.body;
				otherBody = message.body;
			}
			final boolean matchingCounterpart = this.counterpart.equals(message.getCounterpart());
			if (message.getRemoteMsgId() != null) {
				final boolean hasUuid = CryptoHelper.UUID_PATTERN.matcher(message.getRemoteMsgId()).matches();
				if (hasUuid && this.edited != null && matchingCounterpart && this.edited.equals(message.getRemoteMsgId())) {
					return true;
				}
				return (message.getRemoteMsgId().equals(this.remoteMsgId) || message.getRemoteMsgId().equals(this.uuid))
						&& matchingCounterpart
						&& (body.equals(otherBody) ||(message.getEncryption() == Message.ENCRYPTION_PGP && hasUuid));
			} else {
				return this.remoteMsgId == null
						&& matchingCounterpart
						&& body.equals(otherBody)
						&& Math.abs(this.getTimeSent() - message.getTimeSent()) < Config.MESSAGE_MERGE_WINDOW * 1000;
			}
		}
	}

	public Message next() {
		synchronized (this.conversation.messages) {
			if (this.mNextMessage == null) {
				int index = this.conversation.messages.indexOf(this);
				if (index < 0 || index >= this.conversation.messages.size() - 1) {
					this.mNextMessage = null;
				} else {
					this.mNextMessage = this.conversation.messages.get(index + 1);
				}
			}
			return this.mNextMessage;
		}
	}

	public Message prev() {
		synchronized (this.conversation.messages) {
			if (this.mPreviousMessage == null) {
				int index = this.conversation.messages.indexOf(this);
				if (index <= 0 || index > this.conversation.messages.size()) {
					this.mPreviousMessage = null;
				} else {
					this.mPreviousMessage = this.conversation.messages.get(index - 1);
				}
			}
			return this.mPreviousMessage;
		}
	}

	public boolean isLastCorrectableMessage() {
		Message next = next();
		while(next != null) {
			if (next.isCorrectable()) {
				return false;
			}
			next = next.next();
		}
		return isCorrectable();
	}

	private boolean isCorrectable() {
		return getStatus() != STATUS_RECEIVED && !isCarbon();
	}

	public boolean mergeable(final Message message) {
		return message != null &&
				(message.getType() == Message.TYPE_TEXT &&
						this.getTransferable() == null &&
						message.getTransferable() == null &&
						message.getEncryption() != Message.ENCRYPTION_PGP &&
						message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED &&
						this.getType() == message.getType() &&
						//this.getStatus() == message.getStatus() &&
						isStatusMergeable(this.getStatus(), message.getStatus()) &&
						this.getEncryption() == message.getEncryption() &&
						this.getCounterpart() != null &&
						this.getCounterpart().equals(message.getCounterpart()) &&
						this.edited() == message.edited() &&
						(message.getTimeSent() - this.getTimeSent()) <= (Config.MESSAGE_MERGE_WINDOW * 1000) &&
						this.getBody().length() + message.getBody().length() <= Config.MAX_DISPLAY_MESSAGE_CHARS &&
						!message.isGeoUri()&&
						!this.isGeoUri() &&
						!message.treatAsDownloadable() &&
						!this.treatAsDownloadable() &&
						!message.getBody().startsWith(ME_COMMAND) &&
						!this.getBody().startsWith(ME_COMMAND) &&
						!this.bodyIsOnlyEmojis() &&
						!message.bodyIsOnlyEmojis() &&
						((this.axolotlFingerprint == null && message.axolotlFingerprint == null) || this.axolotlFingerprint.equals(message.getFingerprint())) &&
						UIHelper.sameDay(message.getTimeSent(),this.getTimeSent())
				);
	}

	private static boolean isStatusMergeable(int a, int b) {
		return a == b || (
				(a == Message.STATUS_SEND_RECEIVED && b == Message.STATUS_UNSEND)
						|| (a == Message.STATUS_SEND_RECEIVED && b == Message.STATUS_SEND)
						|| (a == Message.STATUS_SEND_RECEIVED && b == Message.STATUS_WAITING)
						|| (a == Message.STATUS_SEND && b == Message.STATUS_UNSEND)
						|| (a == Message.STATUS_SEND && b == Message.STATUS_WAITING)
		);
	}

	public static class MergeSeparator {}

	public SpannableStringBuilder getMergedBody() {
		SpannableStringBuilder body = new SpannableStringBuilder(this.body.trim());
		Message current = this;
		while (current.mergeable(current.next())) {
			current = current.next();
			if (current == null) {
				break;
			}
			body.append("\n\n");
			body.setSpan(new MergeSeparator(), body.length() - 2, body.length(),
					SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
			body.append(current.getBody().trim());
		}
		return body;
	}

	public boolean hasMeCommand() {
		return this.body.trim().startsWith(ME_COMMAND);
	}

	public int getMergedStatus() {
		int status = this.status;
		Message current = this;
		while(current.mergeable(current.next())) {
			current = current.next();
			if (current == null) {
				break;
			}
			status = current.status;
		}
		return status;
	}

	public long getMergedTimeSent() {
		long time = this.timeSent;
		Message current = this;
		while(current.mergeable(current.next())) {
			current = current.next();
			if (current == null) {
				break;
			}
			time = current.timeSent;
		}
		return time;
	}

	public boolean wasMergedIntoPrevious() {
		Message prev = this.prev();
		return prev != null && prev.mergeable(this);
	}

	public boolean trusted() {
		Contact contact = this.getContact();
		return (status > STATUS_RECEIVED || (contact != null && contact.mutualPresenceSubscription()));
	}

	public boolean fixCounterpart() {
		Presences presences = conversation.getContact().getPresences();
		if (counterpart != null && presences.has(counterpart.getResourcepart())) {
			return true;
		} else if (presences.size() >= 1) {
			try {
				counterpart = Jid.fromParts(conversation.getJid().getLocalpart(),
						conversation.getJid().getDomainpart(),
						presences.toResourceArray()[0]);
				return true;
			} catch (InvalidJidException e) {
				counterpart = null;
				return false;
			}
		} else {
			counterpart = null;
			return false;
		}
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getEditedId() {
		return edited;
	}

	public void setOob(boolean isOob) {
		this.oob = isOob;
	}

	private static String extractRelevantExtension(URL url) {
		String path = url.getPath();
		return extractRelevantExtension(path);
	}

	private static String extractRelevantExtension(String path) {
		if (path == null || path.isEmpty()) {
			return null;
		}

		String filename = path.substring(path.lastIndexOf('/') + 1).toLowerCase();
		int dotPosition = filename.lastIndexOf(".");

		if (dotPosition != -1) {
			String extension = filename.substring(dotPosition + 1);
			// we want the real file extension, not the crypto one
			if (Transferable.VALID_CRYPTO_EXTENSIONS.contains(extension)) {
				return extractRelevantExtension(filename.substring(0,dotPosition));
			} else {
				return extension;
			}
		}
		return null;
	}

	public String getMimeType() {
		if (relativeFilePath != null) {
			int start = relativeFilePath.lastIndexOf('.') + 1;
			if (start < relativeFilePath.length()) {
				return MimeUtils.guessMimeTypeFromExtension(relativeFilePath.substring(start));
			} else {
				return null;
			}
		} else {
			try {
				return MimeUtils.guessMimeTypeFromExtension(extractRelevantExtension(new URL(body.trim())));
			} catch (MalformedURLException e) {
				return null;
			}
		}
	}

	public synchronized boolean treatAsDownloadable() {
		if (treatAsDownloadable == null) {
			if (body.trim().contains(" ")) {
				treatAsDownloadable = false;
			}
			try {
				final URL url = new URL(body);
				final String ref = url.getRef();
				final String protocol = url.getProtocol();
				final boolean encrypted = ref != null && AesGcmURLStreamHandler.IV_KEY.matcher(ref).matches();
				treatAsDownloadable = (AesGcmURLStreamHandler.PROTOCOL_NAME.equalsIgnoreCase(protocol) && encrypted)
						|| (("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol)) && (oob || encrypted));

			} catch (MalformedURLException e) {
				treatAsDownloadable = false;
			}
		}
		return treatAsDownloadable;
	}

	public synchronized boolean bodyIsOnlyEmojis() {
		if (isEmojisOnly == null) {
			isEmojisOnly = Emoticons.isOnlyEmoji(body.replaceAll("\\s",""));
		}
		return isEmojisOnly;
	}

	public synchronized boolean isGeoUri() {
		if (isGeoUri == null) {
			isGeoUri = GeoHelper.GEO_URI.matcher(body).matches();
		}
		return isGeoUri;
	}

	public synchronized void resetFileParams() {
		this.fileParams = null;
	}

	public synchronized FileParams getFileParams() {
		if (fileParams == null) {
			fileParams = new FileParams();
			if (this.transferable != null) {
				fileParams.size = this.transferable.getFileSize();
			}
			String parts[] = body == null ? new String[0] : body.split("\\|");
			switch (parts.length) {
				case 1:
					try {
						fileParams.size = Long.parseLong(parts[0]);
					} catch (NumberFormatException e) {
						fileParams.url = parseUrl(parts[0]);
					}
					break;
				case 5:
					fileParams.runtime = parseInt(parts[4]);
				case 4:
					fileParams.width = parseInt(parts[2]);
					fileParams.height = parseInt(parts[3]);
				case 2:
					fileParams.url = parseUrl(parts[0]);
					fileParams.size = parseLong(parts[1]);
					break;
				case 3:
					fileParams.size = parseLong(parts[0]);
					fileParams.width = parseInt(parts[1]);
					fileParams.height = parseInt(parts[2]);
					break;
			}
		}
		return fileParams;
	}

	private static long parseLong(String value) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static int parseInt(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static URL parseUrl(String value) {
		try {
			return new URL(value);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	public void untie() {
		this.mNextMessage = null;
		this.mPreviousMessage = null;
	}

	public boolean isFileOrImage() {
		return type == TYPE_FILE || type == TYPE_IMAGE;
	}

	public boolean hasFileOnRemoteHost() {
		return isFileOrImage() && getFileParams().url != null;
	}

	public boolean needsUploading() {
		return isFileOrImage() && getFileParams().url == null;
	}

	public class FileParams {
		public URL url;
		public long size = 0;
		public int width = 0;
		public int height = 0;
		public int runtime = 0;
	}

	public void setFingerprint(String fingerprint) {
		this.axolotlFingerprint = fingerprint;
	}

	public String getFingerprint() {
		return axolotlFingerprint;
	}

	public boolean isTrusted() {
		FingerprintStatus s = conversation.getAccount().getAxolotlService().getFingerprintTrust(axolotlFingerprint);
		return s != null && s.isTrusted();
	}

	private  int getPreviousEncryption() {
		for (Message iterator = this.prev(); iterator != null; iterator = iterator.prev()){
			if( iterator.isCarbon() || iterator.getStatus() == STATUS_RECEIVED ) {
				continue;
			}
			return iterator.getEncryption();
		}
		return ENCRYPTION_NONE;
	}

	private int getNextEncryption() {
		for (Message iterator = this.next(); iterator != null; iterator = iterator.next()){
			if( iterator.isCarbon() || iterator.getStatus() == STATUS_RECEIVED ) {
				continue;
			}
			return iterator.getEncryption();
		}
		return conversation.getNextEncryption();
	}

	public boolean isValidInSession() {
		int pastEncryption = getCleanedEncryption(this.getPreviousEncryption());
		int futureEncryption = getCleanedEncryption(this.getNextEncryption());

		boolean inUnencryptedSession = pastEncryption == ENCRYPTION_NONE
				|| futureEncryption == ENCRYPTION_NONE
				|| pastEncryption != futureEncryption;

		return inUnencryptedSession || getCleanedEncryption(this.getEncryption()) == pastEncryption;
	}

	private static int getCleanedEncryption(int encryption) {
		if (encryption == ENCRYPTION_DECRYPTED || encryption == ENCRYPTION_DECRYPTION_FAILED) {
			return ENCRYPTION_PGP;
		}
		return encryption;
	}
}
