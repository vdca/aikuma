/*
	Copyright (C) 2013-2015, The Aikuma Project
	AUTHORS: Oliver Adams and Florian Hanke
*/
package org.lp20.aikuma.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.math.NumberUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.lp20.aikuma.Aikuma;
import org.lp20.aikuma.model.FileModel;
import org.lp20.aikuma.model.Recording;
import org.lp20.aikuma.model.Speaker;
import org.lp20.aikuma.model.FileModel.FileType;
import org.lp20.aikuma.storage.Data;
import org.lp20.aikuma.storage.DataStore;
import org.lp20.aikuma.storage.DataStore.StorageException;
import org.lp20.aikuma.storage.FusionIndex2;
import org.lp20.aikuma.storage.google.GoogleDriveIndex;
import org.lp20.aikuma.storage.google.GoogleDriveIndex2;
import org.lp20.aikuma.storage.google.GoogleDriveStorage;
import org.lp20.aikuma.storage.google.TokenManager;
import org.lp20.aikuma.storage.Index;
import org.lp20.aikuma.storage.Utils;
import org.lp20.aikuma.util.AikumaSettings;
import org.lp20.aikuma.util.FileIO;
import org.lp20.aikuma.util.StandardDateFormat;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
/**
 * The service that deals with Google-Cloud API 
 *
 * @author	Sangyeop Lee	<sangl1@student.unimelb.edu.au>
 */
public class GoogleCloudService extends IntentService{
	
	/**
	 * Broadcast message signature
	 */
	public final static String SYNC_RESULT = "org.lp20.aikuma.sync.result";
	/**
	 * Key of data in the broadcast message
	 */
	public final static String SYNC_STATUS = "sync_status";
	
	/**
	 * A list of keys used to start GoogleCloudService
	 */
	public final static String ACTION_KEY = "id";
	/** */
	public final static String ACCOUNT_KEY = "emailAccount";
	/** */
	public final static String TOKEN_KEY = "authToken";
	/** */
	public final static String ARCHIVE_FILE_TYPE_KEY = "type";
	/** */
	public final static String ACTION_EXTRA = "extra";
	
	private final static String TAG = "GoogleCloudService";
	
	private final String approvalKey = AikumaSettings.APPROVED_RECORDING_KEY;
	private final String downloadKey = AikumaSettings.DOWNLOAD_RECORDING_KEY;
	private final String uploadKey = AikumaSettings.UPLOAD_RECORDING_KEY;
	private final String archiveKey = AikumaSettings.ARCHIVED_RECORDING_KEY;
	private final String approvalSpKey = AikumaSettings.APPROVED_SPEAKERS_KEY;
	private final String downloadSpKey = AikumaSettings.DOWNLOAD_SPEAKERS_KEY;
	private final String uploadSpKey = AikumaSettings.UPLOAD_SPEAKERS_KEY;
	private final String archiveSpKey = AikumaSettings.ARCHIVED_SPEAKERS_KEY;
	private final String approvalOtherKey = AikumaSettings.APPROVED_OTHERS_KEY;
	private final String downloadOtherKey = AikumaSettings.DOWNLOAD_OTHERS_KEY;
	private final String uploadOtherKey = AikumaSettings.UPLOAD_OTHERS_KEY;
	private final String archiveOtherKey = AikumaSettings.ARCHIVED_OTHERS_KEY;
	
	// ApprovedSet: CloudIds of Files which will be uploaded 
	// DownloadSet: CloudIds of Files which will be downloaded
	// UploadedSet: CloudIds of Files which is uploaded to private GoogleDrive
	// ArchivedSet:	CloudIds of Files which is archived to central GoogleDrive 
	//									and belongs to the owner(googleEmailAccount)
	private SharedPreferences preferences;
	private Editor prefsEditor;
	private Set<String> approvedRecordingSet;	// Recording which has metadata file
	private Set<String> downloadRecordingSet;
	private Set<String> uploadedRecordingSet;
	private Set<String> archivedRecordingSet;
	private Set<String> approvedSpeakerSet;
	private Set<String> downloadSpeakerSet;
	private Set<String> uploadedSpeakerSet;
	private Set<String> archivedSpeakerSet;
	private Set<String> approvedOtherSet;	// Other items to be uploaded(transcript, preview, mapping)
	private Set<String> downloadOtherSet;	// Other items to be downloaded(transcript, preview, mapping)
	private Set<String> uploadedOtherSet;
	private Set<String> archivedOtherSet;
	
	private boolean forceSync;
	private List<Recording> recordings;
    private List<Speaker> speakers;
    private List<FileModel> others;
	
	private static String googleEmailAccount = "";
	private String googleAuthToken;
	private String googleIdToken;
	private boolean initializeCache;
	
	private int numOfItemsToDownload = 0;
	private boolean isNewRecording = false;
	
	/**
	 * Constructor for IntentService subclasses
	 */
	public GoogleCloudService() {
		super(TAG);
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		String id = intent.getStringExtra(ACTION_KEY);
		String extra = intent.getStringExtra(ACTION_EXTRA);
		
		googleAuthToken = intent.getStringExtra(TOKEN_KEY);
		
		List<String> googleAccountList;
		if(googleAuthToken == null && extra != null && extra.equals("all")) {	
			// token can be null if the device is offline
			// when the automatic-sync is checked or 
			// when the connectivity changes after the automatic-sync is enabled
			googleAccountList = intent.getStringArrayListExtra(ACCOUNT_KEY);
		} else {
			googleAccountList = new ArrayList<String>();
			googleAccountList.add(intent.getStringExtra(ACCOUNT_KEY));
		}
		
		forceSync = intent.getBooleanExtra("forceSync", false);
		
		Log.i(TAG, "device-connectivity: " + Aikuma.isDeviceOnline());
		// If this is triggered by (CloudSettingsActivity or BootReceiver),
		// sync happens across all accounts.
		for(String googleAccount : googleAccountList) {
			Log.i(TAG, "intent-action: " + id + " using " + googleAccount);
			
			if(!googleEmailAccount.equals(googleAccount)) {
				googleEmailAccount = googleAccount;
				initializeCache = true;
			}
			
			prepareSettings(googleEmailAccount);
			
			if(id.equals("sync")) {
				backUp();
				autoDownloadFiles();
				validateToken();
				retryBackup();
				retryDownload();
			} else if(id.equals("retry")) {	// Called with the start of application
				validateToken();
				retryBackup();
				retryDownload();
			} else if(id.equals("backup")) {	// Called when backup-setting is enabled
				backUp();
				validateToken();
				retryBackup(); 
			} else if(id.equals("autoDownload")) {
				autoDownloadFiles();
				validateToken();
				
				List<String> itemCloudIdsToDownload = intent.getStringArrayListExtra("downloadItems");
				if(itemCloudIdsToDownload != null) {
					// Copy Files to private-storage
					copyFilesToPrivateStorage(itemCloudIdsToDownload);
					
					//downloadOtherSet.addAll(itemCloudIdsToDownload);
					prepareUploadSet();
					autoDownloadFiles();	// Logging an update of copied files in private-storage
					
					//Log the files in archivedSet
					for(String itemCloudId : itemCloudIdsToDownload) {
						classifyFileCloudId(itemCloudId, 
								archivedRecordingSet, archivedSpeakerSet, archivedOtherSet);
					}
					prefsEditor.putStringSet(archiveKey, archivedRecordingSet);
			        prefsEditor.putStringSet(archiveSpKey, archivedSpeakerSet);
			        prefsEditor.putStringSet(archiveOtherKey, archivedOtherSet);
			        
			        prefsEditor.commit();
				}
				
				retryDownload();
			} else {			// Called when archive button is pressed or new-file is created
								// (token was already validated)
				String itemType = (String)
						intent.getExtras().get("type");
				// id : (version)-(file's ID)
				if(itemType.equals("archive"))
					archive(id, null, 0, true);
				else if(itemType.equals("recording"))
					archive(id, null, 0, false);
				else if(itemType.equals("tag")) {
					ArrayList<String> tagVerIdList = 
							intent.getStringArrayListExtra(ACTION_EXTRA);
					for(String tagVerId : tagVerIdList) {
						archive(tagVerId, id, 2, false);
					}
				}
				else
					archive(id, null, 1, false);
				
				validateToken();
				
				retryBackup();
			}
			
			googleAuthToken = null;
		}
		
		// Create an index file after cloud-activity is finished
		if(isNewRecording) {
			try {
				Recording.indexAll();
			} catch (IOException e) {
				Log.e(TAG, e.getMessage());
			}	
		}
		
		broadcastStatus("end");
	}
	
	private void prepareSettings(String emailAccount) {

		preferences = this.getSharedPreferences(emailAccount, MODE_PRIVATE);

		approvedRecordingSet = (HashSet<String>)
				preferences.getStringSet(approvalKey, new HashSet<String>());
		downloadRecordingSet = (HashSet<String>)
				preferences.getStringSet(downloadKey, new HashSet<String>());
		uploadedRecordingSet = (HashSet<String>)
				preferences.getStringSet(uploadKey, new HashSet<String>());
		archivedRecordingSet = (HashSet<String>)
				preferences.getStringSet(archiveKey, new HashSet<String>());
		
		approvedSpeakerSet = (HashSet<String>)
				preferences.getStringSet(approvalSpKey, new HashSet<String>());
		downloadSpeakerSet = (HashSet<String>)
				preferences.getStringSet(downloadSpKey, new HashSet<String>());
		uploadedSpeakerSet = (HashSet<String>)
				preferences.getStringSet(uploadSpKey, new HashSet<String>());
		archivedSpeakerSet = (HashSet<String>)
				preferences.getStringSet(archiveSpKey, new HashSet<String>());
		
		approvedOtherSet = (HashSet<String>)
				preferences.getStringSet(approvalOtherKey, new HashSet<String>());
		downloadOtherSet = (HashSet<String>)
				preferences.getStringSet(downloadOtherKey, new HashSet<String>());
		uploadedOtherSet = (HashSet<String>)
				preferences.getStringSet(uploadOtherKey, new HashSet<String>());
		archivedOtherSet = (HashSet<String>)
				preferences.getStringSet(archiveOtherKey, new HashSet<String>());
		
		prefsEditor = preferences.edit();
		
		if(!Aikuma.isDeviceOnline())
			return;
		else if(googleAuthToken == null) {
			try {
				googleAuthToken = GoogleAuthUtil.getToken(
						getApplicationContext(), googleEmailAccount, AikumaSettings.getScope());
			} catch (Exception e) {
				Log.e(TAG, e.getMessage());
			}
		}
			
		if(archivedRecordingSet.size() + archivedSpeakerSet.size() + archivedOtherSet.size() == 0 || forceSync) {
			prepareUploadSet();
			prepareArchiveSet();
		}
		
		Log.i(TAG, "Cloud created(recording-approve):" + approvedRecordingSet.toString());
		Log.i(TAG, "CLoud created(recording-download):" + downloadRecordingSet.toString());
		Log.i(TAG, "CLoud created(recording-uploaded):" + uploadedRecordingSet.toString());
		Log.i(TAG, "Cloud created(recording-archive):" + archivedRecordingSet.toString());
		Log.i(TAG, "Cloud created(speaker-approve):" + approvedSpeakerSet.toString());
		Log.i(TAG, "CLoud created(speaker-download):" + downloadSpeakerSet.toString());
		Log.i(TAG, "CLoud created(speaker-uploaded):" + uploadedSpeakerSet.toString());
		Log.i(TAG, "Cloud created(speaker-archive):" + archivedSpeakerSet.toString());
		Log.i(TAG, "Cloud created(other-approve):" + approvedOtherSet.toString());
		Log.i(TAG, "CLoud created(other-download):" + downloadOtherSet.toString());
		Log.i(TAG, "CLoud created(other-uploaded):" + uploadedOtherSet.toString());
		Log.i(TAG, "Cloud created(otherr-archive):" + archivedOtherSet.toString());
	}
	
	private void prepareUploadSet() {
		DataStore gd;
		try {
			gd = new GoogleDriveStorage(googleAuthToken, 
					AikumaSettings.ROOT_FOLDER_ID, AikumaSettings.CENTRAL_USER_ID, initializeCache);
			initializeCache = false;
		} catch (DataStore.StorageException e) {
			Log.e(TAG, "Failed to initialize GoogleDriveStorage");
			return;
		}

		Log.i(TAG, "Start investigating private GoogleDrive: " + googleEmailAccount);
		// Collect archived file-cloudIDs in GoogleCloud
		// For the case when there are too many files in GoogleDrive, gd.list uses a custom field
        gd.list(new GoogleDriveStorage.ListItemHandler() {
			@Override
			public boolean processItem(String identifier, Date date) {
				// Classify identifiers and store them in different sets 
				classifyFileCloudId(identifier, 
						uploadedRecordingSet, uploadedSpeakerSet, uploadedOtherSet);
				return true;
			}
		});
        
        prefsEditor.putStringSet(uploadKey, uploadedRecordingSet);
        prefsEditor.putStringSet(uploadSpKey, uploadedSpeakerSet);
        prefsEditor.putStringSet(uploadOtherKey, uploadedOtherSet);
        
        prefsEditor.commit();
	}
	
	private void prepareArchiveSet() {
		Index fi;
		//fi = new FusionIndex2(AikumaSettings.getIndexServerUrl(), 
		//		googleIdToken, googleAuthToken);
		try {
			fi = new GoogleDriveIndex2(AikumaSettings.ROOT_FOLDER_ID, new TokenManager() {
			    @Override
			    public String accessToken() {
			        return googleAuthToken;
			    }
			}, AikumaSettings.getIndexServerUrl(), googleIdToken);
		} catch (StorageException e) {
			Log.e(TAG, "Root folder doesn't exist");
			return;
		}
		Map<String, String> constraints = new HashMap<String, String>();
		constraints.put(Recording.USER_ID_KEY, googleEmailAccount);
		
		Log.i(TAG, "Start investigating central storage: " + googleEmailAccount);
		fi.search(constraints, new Index.SearchResultProcessor() {
			@Override
			public boolean process(Map<String, String> result) {
				String identifier = result.get("identifier");
				classifyFileCloudId(identifier, 
						archivedRecordingSet, archivedSpeakerSet, archivedOtherSet);
				
				return true;
			}
		});
		
		prefsEditor.putStringSet(archiveKey, archivedRecordingSet);
        prefsEditor.putStringSet(archiveSpKey, archivedSpeakerSet);
        prefsEditor.putStringSet(archiveOtherKey, archivedOtherSet);
        
        prefsEditor.commit();
	}
	
	private void copyFilesToPrivateStorage(List<String> cloudIdsToCopy) {
		DataStore gd;
		try {
			gd = new GoogleDriveStorage(googleAuthToken, 
					AikumaSettings.ROOT_FOLDER_ID, AikumaSettings.CENTRAL_USER_ID, initializeCache);
			initializeCache = false;
		} catch (DataStore.StorageException e) {
			Log.e(TAG, "Failed to initialize GoogleDriveStorage");
			return;
		}
		
		for(String cloudId : cloudIdsToCopy) {
			gd.copy(cloudId);
		}

	}
	
	// Classify all Aikuma files except for metadata-file
	private void classifyFileCloudId(String identifier, 
			Set<String> recordingSet, Set<String> speakerSet, Set<String> otherSet) {
		Log.i(TAG, "classify: " + identifier);
		if(FileModel.checkCloudFormat(identifier)) {
			String currentVersionName = AikumaSettings.getLatestVersion();
			String relPath = FileModel.getRelPath(identifier);
			Log.i(TAG, "rel: " + relPath);
			if((FileModel.checkType(identifier, currentVersionName, FileType.METADATA) && !relPath.endsWith(Speaker.PATH)) || 
					!identifier.startsWith(currentVersionName))
				return;
			
			if(relPath.endsWith(Recording.PATH)) {
				if(FileModel.checkType(identifier, currentVersionName, FileType.PREVIEW)) {
					Log.i(TAG, "preview added");
					otherSet.add(identifier);
				} else if(identifier.endsWith(FileModel.AUDIO_EXT) || 
						identifier.endsWith(FileModel.VIDEO_EXT) || 
						FileModel.checkType(identifier, currentVersionName, FileType.SEGMENT)) {
					Log.i(TAG, "rec added");
					recordingSet.add(identifier);
				} else {
					Log.i(TAG, "rec-other added");
					otherSet.add(identifier);
				}
			} else if(relPath.endsWith(Speaker.PATH)) {
				Log.i(TAG, "speaker added");
				speakerSet.add(identifier);
			} else if(relPath.endsWith(Recording.TAG_PATH)) {
				Log.i(TAG, "tag added");
				otherSet.add(identifier);
			}
		}
	}
	
	private void collectItemsInDevice() {
		if(recordings != null && speakers != null && others != null)
			return;
		
		// Read only the latest version files
		recordings = Recording.readAll();
        speakers = Speaker.readAll();
        others = new ArrayList<FileModel>();
        others.addAll(Recording.readAllTags());
        
        for(Recording recording : recordings) {
			if(recording.isOriginal()) {
				if(recording.getTranscriptFile() != null) {
					others.add(new FileModel(recording.getVersionName(), recording.getOwnerId(), 
							recording.getTranscriptId(), FileModel.TRANSCRIPT_TYPE, FileModel.TEXT_EXT));
				}
				if(recording.getPreviewFile() != null) {
					others.add(new FileModel(recording.getVersionName(), recording.getOwnerId(), 
							recording.getPreviewId(), FileModel.PREVIEW_TYPE, FileModel.AUDIO_EXT));
				}
				if(recording.getImageFile() != null) {
					others.add(new FileModel(recording.getVersionName(), recording.getOwnerId(), 
							recording.getImageId(), FileModel.IMAGE_TYPE, FileModel.IMAGE_EXT));
				}
			} else if(!recording.getFileType().equals(Recording.SEGMENT_TYPE)){
				others.add(new FileModel(recording.getVersionName(), recording.getOwnerId(), 
						recording.getMapId(), FileModel.MAPPING_TYPE, FileModel.JSON_EXT));
			}
		}
	}
	
	private void autoDownloadFiles() {
		// Search the items not existing in this device  
        // 1. Collect files in device
        collectItemsInDevice();
        
        // 2. Filtering
        logFileInDownloadSet(uploadedRecordingSet, false, downloadKey, downloadRecordingSet, recordings);
        logFileInDownloadSet(uploadedSpeakerSet, false, downloadSpKey, downloadSpeakerSet, speakers);
        logFileInDownloadSet(uploadedOtherSet, true, downloadOtherKey, downloadOtherSet, others);
        
        numOfItemsToDownload = downloadRecordingSet.size();
        
        prefsEditor.commit();
	}
	
	private void retryDownload() {
		if(googleAuthToken == null || !Aikuma.isDeviceOnline())
			return;
        
		DataStore gd;
		try {
			gd = new GoogleDriveStorage(googleAuthToken, 
					AikumaSettings.ROOT_FOLDER_ID, AikumaSettings.CENTRAL_USER_ID, initializeCache);
			initializeCache = false;
		} catch (DataStore.StorageException e) {
			Log.e(TAG, "Failed to initialize GoogleDriveStorage");
			return;
		}
		
		retryDownload(gd, 2, downloadOtherKey, downloadOtherSet, uploadOtherKey, uploadedOtherSet);
        retryDownload(gd, 2, downloadSpKey, downloadSpeakerSet, uploadSpKey, uploadedSpeakerSet);
        retryDownload(gd, 0, downloadKey, downloadRecordingSet, uploadKey, uploadedRecordingSet);
        
        if(numOfItemsToDownload - downloadRecordingSet.size() > 0) {
        	isNewRecording = true;
        }
	}
	
	private void retryDownload(DataStore gd, int state, 
			String dlKey, Set<String> downloadSet, String upKey, Set<String> uploadedSet) {
		Set<String> itemIdentifiers = new HashSet<String>(downloadSet);
		
		for(String itemIdentifier : itemIdentifiers) {
			Log.i(TAG, "download: " + itemIdentifier + " | " + state);
			broadcastStatus("start");
			FileModel item = FileModel.fromCloudId(itemIdentifier);
			
			String relPath = itemIdentifier.substring(0, itemIdentifier.lastIndexOf('/'));
			File dir = new File(FileIO.getAppRootPath(), relPath);
			dir.mkdirs();
			Log.i(TAG, dir.getAbsolutePath() + ", " + item.getMetadataIdExt());
			try {
				switch(state) {
				case 0:
				case 2:
					InputStream is = gd.load(itemIdentifier);
					FileOutputStream fos = 
							new FileOutputStream(new File(dir, item.getIdExt()));
					if(is == null || fos == null)
						break;
					Utils.copyStream(is, fos, true);
					if(state == 2)
						break;
				case 1:
					InputStream metaIs = gd.load(item.getCloudIdentifier(1));
					FileOutputStream metaFos = 
							new FileOutputStream(new File(dir, item.getMetadataIdExt()));
					if(metaIs == null || metaFos == null)
						break;
					Utils.copyStream(metaIs, metaFos, true);
				}
				downloadSet.remove(itemIdentifier);
				prefsEditor.putStringSet(dlKey, downloadSet);
				prefsEditor.commit();
				
				broadcastStatus(item.getVersionName() + "-" + item.getId());
			} catch (FileNotFoundException e) {
        		Log.e(TAG, "no-found:" + e.getMessage());
        	} catch (IOException e) {
    			Log.e(TAG, e.getMessage());
    		}
			
		}
	}
	
	
	/**
	 *  Back-up function: add all recording/speaker-items to the Set (Log all items)
	 *  (and retry() will upload the items)
	 */
	private void backUp() {
		collectItemsInDevice();

		logFileInApprovalSet(recordings, false, false, approvalKey, approvedRecordingSet, 
				uploadedRecordingSet, uploadedRecordingSet);
		logFileInApprovalSet(speakers, true, false, approvalSpKey, approvedSpeakerSet, 
				uploadedSpeakerSet, uploadedSpeakerSet);
		logFileInApprovalSet(others, true, false, approvalOtherKey, approvedOtherSet, 
				uploadedOtherSet, uploadedOtherSet);
		
		prefsEditor.commit();
	}

	/**
	 *  Retry function: Upload all recording-items in the Set
	 */
	private void retryBackup() {
		if(googleAuthToken == null || !Aikuma.isDeviceOnline())
			return;
		DataStore gd;
		try {
			gd = new GoogleDriveStorage(googleAuthToken, 
					AikumaSettings.ROOT_FOLDER_ID, AikumaSettings.CENTRAL_USER_ID, initializeCache);
			initializeCache = false;
		} catch (DataStore.StorageException e) {
			Log.e(TAG, "Failed to initialize GoogleDriveStorage");
			return;
		}
		
		Index fi;
		//fi = new FusionIndex2(AikumaSettings.getIndexServerUrl(), googleIdToken, googleAuthToken);
		try {
			fi = new GoogleDriveIndex2(AikumaSettings.ROOT_FOLDER_ID, new TokenManager() {
			    @Override
			    public String accessToken() {
			        return googleAuthToken;
			    }
			}, AikumaSettings.getIndexServerUrl(), googleIdToken);
		} catch (StorageException e) {
			Log.e(TAG, "Root folder doesn't exist");
			return;
		}		
		
		// Others
		retryBackup(gd, fi, approvalOtherKey, approvedOtherSet, 
				uploadOtherKey, uploadedOtherSet, archiveOtherKey, archivedOtherSet);

		// Speakers
		retryBackup(gd, fi, approvalSpKey, approvedSpeakerSet,
				uploadSpKey, uploadedSpeakerSet, archiveSpKey, archivedSpeakerSet);
		
		// Recordings
		retryBackup(gd, fi, approvalKey, approvedRecordingSet, 
				uploadKey, uploadedRecordingSet, archiveKey, archivedRecordingSet);
		
	}
	
	private void retryBackup(DataStore gd, Index fi, String apKey, Set<String> approvedSet, 
			String upKey, Set<String> uploadedSet, String arKey, Set<String> archivedSet) {
		Set<String> itemIdentifiers = new HashSet<String>(approvedSet);

		for(String itemIdentifier : itemIdentifiers) {
			FileModel item = FileModel.fromCloudId(itemIdentifier);
			if(!archivedSet.contains(itemIdentifier)) {
				broadcastStatus("start");
				
				// Get the current state of archiving
				String[] requestArchiveState = 
						preferences.getString(itemIdentifier, "").split("\\|");
				String requestDate = requestArchiveState[0];
				int archiveProgress = 
						Integer.parseInt(requestArchiveState[1]);
				
				if(archiveProgress != 2 && uploadedSet.contains(itemIdentifier)) {
					updateApprovalArchiveSet(itemIdentifier, 
							apKey, approvedSet, upKey, uploadedSet);
					return;
				}
				
				String uri = null;
				if (requestArchiveState.length >= 3 && requestArchiveState[2].length() > 0)
					uri = requestArchiveState[2];
					
				try {
					Log.i(TAG, "startArchiving: " + itemIdentifier);
					startArchiving(gd, fi, item, requestDate, uri, archiveProgress,
							apKey, approvedSet, upKey, uploadedSet, arKey, archivedSet);
				} catch (IOException e) {
					Log.e(TAG, e.getMessage());
				}
			} else {
				updateApprovalArchiveSet(itemIdentifier, 
						apKey, approvedSet, arKey, archivedSet);
			}	
		}
		
	}
	
	/**
	 * Log the recording/speaker-item of id to be uploaded or archived
	 * 
	 * @param verId	Version and ID of the recording item
	 * @param extra	Extra information for upload or archive
	 * @param type	Type of the item(0: recording, 1: speaker)
	 * @param isArchive	(true: archive, false: upload) recording-related files
	 */
	private void archive(String verId, String extra, int type, boolean isArchive) {
		String[] splitName = verId.split("-");
		
		String versionName = splitName[0];
		String ownerId = splitName[2];
		
		try {
			if(type == 0) {	// Recording
				String id = verId.substring(4);
				
				Recording recording = Recording.read(versionName, ownerId, id);
				List<Recording> oneRecording = new ArrayList<Recording>();
				oneRecording.add(recording);
				List<? extends FileModel> recordingSpeakers = recording.getSpeakers();
				List<FileModel> other = new ArrayList<FileModel>();
				if(recording.isOriginal()) {
					if (recording.getTranscriptFile() != null) {
						other.add(new FileModel(recording.getVersionName(), recording.getOwnerId(), 
								recording.getTranscriptId(), FileModel.TRANSCRIPT_TYPE, FileModel.TEXT_EXT));
					}
					if (recording.getPreviewFile() != null) {
						other.add(new FileModel(recording.getVersionName(), recording.getOwnerId(), 
								recording.getPreviewId(), FileModel.PREVIEW_TYPE, FileModel.AUDIO_EXT));
					}
					if(recording.getImageFile() != null) {
						other.add(new FileModel(recording.getVersionName(), recording.getOwnerId(), 
								recording.getImageId(), FileModel.IMAGE_TYPE, FileModel.IMAGE_EXT));
					}
				} else if(!recording.getFileType().equals(Recording.SEGMENT_TYPE)){
					other.add(new FileModel(recording.getVersionName(), recording.getOwnerId(), 
							recording.getMapId(), FileModel.MAPPING_TYPE, FileModel.JSON_EXT));
				}
				other.addAll(recording.getTags());
				
				if(isArchive) {
					logFileToArchive(oneRecording, approvalKey, approvedRecordingSet, 
							uploadKey, uploadedRecordingSet, archivedRecordingSet);
					logFileToArchive(recordingSpeakers, approvalSpKey, approvedSpeakerSet, 
							uploadSpKey, uploadedSpeakerSet, archivedSpeakerSet);
					logFileToArchive(other, approvalOtherKey, approvedOtherSet, 
							uploadOtherKey, uploadedOtherSet, archivedOtherSet);
				} else {
					logFileInApprovalSet(oneRecording, false, false,
							approvalKey, approvedRecordingSet, uploadedRecordingSet, uploadedRecordingSet);
					logFileInApprovalSet(recordingSpeakers, true, false,
							approvalSpKey, approvedSpeakerSet, uploadedSpeakerSet, uploadedSpeakerSet);
					logFileInApprovalSet(other, true, false,
							approvalOtherKey, approvedOtherSet, uploadedOtherSet, uploadedOtherSet);
				}

			} else if (type == 2) {	// Tag
				String id = verId.substring(4);
				FileModel tagFileModel = 
						new FileModel(versionName, extra, id, FileModel.TAG_TYPE, "");
				List<FileModel> tags = new ArrayList<FileModel>();
				tags.add(tagFileModel);
				
				logFileInApprovalSet(tags, true, false,
						approvalOtherKey, approvedOtherSet, uploadedOtherSet, uploadedOtherSet);
			} else {				// Speaker
				String id = splitName[1];
				
				Speaker speaker = Speaker.read(versionName, ownerId, id);
				List<Speaker> speakers = new ArrayList<Speaker>();
				speakers.add(speaker);
				
				logFileInApprovalSet(speakers, true, false, approvalSpKey, approvedSpeakerSet, 
						uploadedSpeakerSet, uploadedSpeakerSet);
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.e(TAG, "archive failed: " + verId);
		}

		prefsEditor.commit();
	}
	
	/**
	 *  Log the file approved to be downloaded
	 *  (Recording)			 		: 0(metadata-download) -> 1(file-download)
	 *  (Speaker)					:						  1(metadataFile-download)
	 *  (Others:mapping, transcript): 						  1(file-download)
	 */
	private void logFileInDownloadSet(Set<String> archivedSet, boolean isOtherType,
			String dlKey, Set<String> downloadSet, List<? extends FileModel> items) {
		Set<String> deviceItemIdSet = new HashSet<String>();
		for(FileModel item : items) {
			deviceItemIdSet.add(item.getCloudIdentifier(0));
		}
		
		for(String archivedItemId : archivedSet) {
			if(!deviceItemIdSet.contains(archivedItemId)) {
				downloadSet.add(archivedItemId);
			}
		}

		prefsEditor.putStringSet(dlKey, downloadSet);
	}
	
	
	/**
	 *  Log the file approved to be uploaded
	 *  MyFile			   			(Source, Respeaking) : 4(file-upload) -> 5(metadata upload)
	 *  							can be changed to 	   0(file-upload) -> 1(metadata upload)  -> 2(indexing in FusionTable)
	 *  MyFile								   (Speaker) : 6(metadataFile-upload)
	 *  							can be changed to	 : 3(metadataFile-upload)				 -> 2(indexing)
	 *  MyFile		(mapping, transcript, preview, image): 6(file-upload)
	 *  (others)					can be changed to	   3(file-upload)                        -> 2(indexing in FusionTable)
	 *  Other-owner's file (Source, Respeaking, Speaker) : 4(file-upload) -> 5(metadata upload)
	 *  Other-owner's file(mapping, transcript, preview) : 6(file-upload)
	 */
	private void logFileInApprovalSet(List<? extends FileModel> items, boolean isOtherType, boolean isArchive,
			String apKey, Set<String> approvedSet, Set<String> uploadedSet, Set<String> archivedSet) {
		String requestDate = new StandardDateFormat().
				format(new Date()).toString();
		String archiveState;
		
		for(FileModel item : items) {
			String identifier = item.getCloudIdentifier(0);
			if(!archivedSet.contains(identifier) && !approvedSet.contains(identifier) &&
					(!isArchive || uploadedSet.contains(identifier))) {
				approvedSet.add(identifier);
				
//				String ownerId = item.getOwnerId();
				
				if(isArchive) {
					archiveState = (requestDate + "|2");
				} else {
					if(isOtherType) {
						archiveState = (requestDate + "|6");
					} else {
						archiveState = (requestDate + "|4");
					}
				}
				
				prefsEditor.putString(identifier, archiveState);
			}
		}
		
		prefsEditor.putStringSet(apKey, approvedSet);
	}
	
	private void logFileToArchive(List<? extends FileModel> items, String apKey, Set<String> approvedSet, 
			String upKey, Set<String> uploadedSet, Set<String> archivedSet) {
		for(FileModel fm : items) {
			changeState(fm.getCloudIdentifier(0), apKey, approvedSet, 
					upKey, uploadedSet, archivedSet);
		}
	}

	/**
	 * Check the file state and change the state to make it be archived
	 * 								  				  State-change
	 * Approved (file, metadata)					: 4 -> 0
	 *  		(file)								: 6 -> 3
	 * Partially uploaded (metadata not uploaded)	: 5 -> 1
	 * Fully Uploaded								: 2 (new state added)
	 */
	private void changeState(String itemCloudId, String apKey, Set<String> approvedSet, 
			String upKey, Set<String> uploadedSet, Set<String> archivedSet) {
		Log.i(TAG, "changeState(state, approved, uploaded, archived): " + 
			itemCloudId + "(" + preferences.getString(itemCloudId, "NULL") + ", " + 
			approvedSet.contains(itemCloudId) + ", " + uploadedSet.contains(itemCloudId) + ", " + 
			archivedSet.contains(itemCloudId) + ")");
		
		if(archivedSet.contains(itemCloudId))
			return;
		
		if(uploadedSet.contains(itemCloudId)) {
			if(approvedSet.contains(itemCloudId))
				return;
			
			FileModel fm = FileModel.fromCloudId(itemCloudId);
			List<FileModel> items = new ArrayList<FileModel>();
			items.add(fm);
			
			String fileType = fm.getFileType();
			
			if(/* fileType.equals(FileModel.SPEAKER_TYPE) || */fileType.equals(FileModel.SOURCE_TYPE) || 
					fileType.equals(FileModel.RESPEAKING_TYPE) || fileType.equals(FileModel.TRANSLATION_TYPE)) {
				logFileInApprovalSet(items, false, true, apKey, approvedSet, uploadedSet, archivedSet);
			} else {
				logFileInApprovalSet(items, true, true, apKey, approvedSet, uploadedSet, archivedSet);
			}
		
		} else {
			if(approvedSet.contains(itemCloudId)) {
				String itemArchiveState = preferences.getString(itemCloudId, "");
				
				String[] requestArchiveState = itemArchiveState.split("\\|");
				String requestDate = requestArchiveState[0];
				int archiveProgress = 
						Integer.parseInt(requestArchiveState[1]);
				
				String uri = "";
				if (requestArchiveState.length >= 3 && requestArchiveState[2].length() > 0)
					uri = requestArchiveState[2];
				
				switch(archiveProgress) {
				case 4:
					itemArchiveState = (requestDate + "|0");
					break;
				case 5:
					itemArchiveState = (requestDate + "|1|" + uri);
					break;
				case 6:
					itemArchiveState = (requestDate + "|3");
					break;
				}
				// Overwrite the old state with a new state
				prefsEditor.putString(itemCloudId, itemArchiveState);
			} else {
				// This will not happen because all items are uploaded to private GoogleDrive
			}
		}
	}
	
	// If the recording/speaker-item archiving is finished, update the Approval/Archive Set
	private void updateApprovalArchiveSet(String itemCloudId,
			String apKey, Set<String> approvedSet, String arKey, Set<String> archivedSet) {
		prefsEditor.remove(itemCloudId);
		
		approvedSet.remove(itemCloudId);
		prefsEditor.putStringSet(apKey, approvedSet);
		
		archivedSet.add(itemCloudId);
		prefsEditor.putStringSet(arKey, archivedSet);
		prefsEditor.commit();
	}
	
	/**
	 * Start uploading the recording-item
	 * Archiving-state(0:approved, 1:File-uploaded, 
	 * 2:FusionTabe-index-finished and [recording]-archive.json file is created)
	 * 
	 * @param recording		The recording-item to be archived
	 * @param requestDate	Archive-approval date
	 * @param state			Archiving-state
	 * @throws IOException	Exception during file-processing
	 */
	
	private void startArchiving(DataStore gd, Index fi, FileModel item, String requestDate, String uri, int state,
			String apKey, Set<String> approvedSet, String upKey, Set<String> uploadedSet, 
			String arKey, Set<String> archivedSet) throws IOException {
		String identifier = item.getCloudIdentifier(0);	// path + id + [.wav|.mp4|-small-image.jpg|.txt]
		String itemArchiveState;
		File itemFile;
		Log.i(TAG, "cloud-Id: " + identifier + ", state: " + state);
		
		switch(state) {
		case 0:
		case 3:
		case 4:
		case 6:	
			itemFile = item.getFile(0);
			uri = uploadFile(gd, itemFile, identifier);

			if(uri == null) return;

			if(state == 0) {
				state = 1;
				itemArchiveState = (requestDate + "|1|" + uri);
			}
			else if(state == 3) {
				state = 2;
				itemArchiveState = (requestDate + "|2|" + uri);
			}
			else if(state == 4) {
				state = 5;
				itemArchiveState = (requestDate + "|5|" + uri);
			} else {
				updateApprovalArchiveSet(identifier,
						apKey, approvedSet, upKey, uploadedSet);
				return;
			}
			
			prefsEditor.putString(identifier, itemArchiveState);
			prefsEditor.commit();
		case 1:
		case 5:
			if(state != 2) {
				String metaIdentifier = item.getCloudIdentifier(1);		// path + id(recording/speaker) + [-metadata.json]
				Log.i(TAG, "metafile-cloud-Id: " + metaIdentifier + ", state: " + state);
				itemFile = item.getFile(1);
				uri = uploadFile(gd, itemFile, metaIdentifier);

				if(uri == null) return;
				
				if(state == 1) {
					itemArchiveState = (requestDate + "|2|" + uri);
					uploadedSet.add(identifier);
					prefsEditor.putString(identifier, itemArchiveState);
					prefsEditor.putStringSet(upKey, uploadedSet);
					prefsEditor.commit();
				} else {
					updateApprovalArchiveSet(identifier, 
							apKey, approvedSet, upKey, uploadedSet);
					return;
				}
			}	
		case 2:
			Date uploadDate = 
				uploadMetadata(fi, gd, item, identifier, requestDate, uri);
			if(uploadDate == null) return;
			
			updateApprovalArchiveSet(identifier,
					apKey, approvedSet, arKey, archivedSet);
			return;
		}
	}

	
	//upload the file to Google Drive
	private String uploadFile(DataStore gd, File file, String identifier) throws IOException {
		
		Data data = Data.fromFile(file);
		if (data == null) {
			Log.e(TAG, "Source file doesn't exist(file:" + file.getName() + ", ID:" + identifier +")");
			return null;		
		}
		
		String uri = gd.store(identifier, data);
		if(uri != null)
			Log.i(TAG, "File-upload success");
		else
			Log.i(TAG, "File-Upload failed");
		return uri;
	}
	
	// Upload the metadata of the recording to FusionTable
	private Date uploadMetadata(Index fi, DataStore gd,
			FileModel item, String identifier, String requestDate, String uri) throws IOException {
		
		Date uploadDate = new Date();
		Map<String, String> metadata = new HashMap<String,String>();
		
		String[] splitName = item.getId().split("-");
		String emailAddr = item.getOwnerId();
		if (emailAddr == null) {
			Log.e(TAG, "itemOwnerAccount is null");
			return null;
		}
		if (uri == null) {
			uri = "";
		}
		
		File metadataFile = item.getFile(1);
		if(metadataFile != null) {	// Recording or Speaker
			String jsonstr;
			try {
				jsonstr = FileIO.read(metadataFile);
			} catch (IOException e) {
				Log.e(TAG, "Failed to read metadata file: " + metadataFile.getPath());
				return null;
			}
			JSONObject jsonfile = (JSONObject) JSONValue.parse(jsonstr);

			String groupId;
			String speakers = "";
			String joiner = "";
			if(item.getFileType().equals(FileModel.SPEAKER_TYPE)) {
				groupId = (String) jsonfile.get(Speaker.SPEAKER_ID_KEY);
				
				metadata.put(Speaker.SPEAKER_ID_PREFIX_KEY, groupId.substring(0, 4));
				metadata.put(Speaker.NAME_KEY, (String) jsonfile.get(Speaker.NAME_KEY));
				metadata.put(Speaker.VERSION_KEY, (String) jsonfile.get(Speaker.VERSION_KEY));
				String simpleDate = (String) jsonfile.get(Speaker.DATE_KEY);
				try {
					simpleDate = new SimpleDateFormat("yyyyMMdd")
										.format(new StandardDateFormat().parse(simpleDate));
					metadata.put(Speaker.DATE_KEY, simpleDate);
				} catch (ParseException e) {
					Log.e(TAG, "date parsing error");
				}
			} else {
				groupId = (String) jsonfile.get(Recording.ITEM_ID_KEY);
				
//				JSONArray speakers_arr = (JSONArray) jsonfile.get(Recording.SPEAKERS_KEY);
//				for (Object obj: speakers_arr) {
//					speakers += joiner + (String) obj;
//					joiner = ",";
//				}
				metadata.put(Recording.ITEM_ID_PREFIX_KEY, groupId.substring(0, 4));
				metadata.put(Recording.NAME_KEY, (String) jsonfile.get(Recording.NAME_KEY)); //For derivatives/segments, this can be an empty string
				metadata.put(Recording.VERSION_KEY, (String) jsonfile.get(Recording.VERSION_KEY));
				String simpleDate = (String) jsonfile.get(Recording.DATE_KEY);
				try {
					simpleDate = new SimpleDateFormat("yyyyMMdd")
										.format(new StandardDateFormat().parse(simpleDate));
					metadata.put(Recording.DATE_KEY, simpleDate);
				} catch (ParseException e) {
					Log.e(TAG, "date parsing error");
				}
				
				if(!item.getFileType().equals(FileModel.SEGMENT_TYPE)) {
					String durRangeStr = "" + 
							Recording.DurRange.fromDurationMsec(((Long)jsonfile.get(Recording.DURATION_MSEC_KEY)).intValue());
					metadata.put(Recording.DURATION_CLOUD_KEY, durRangeStr);
				}
				
				metadata.put(Recording.ANDROID_ID_KEY, (String) jsonfile.get(Recording.ANDROID_ID_KEY));
				metadata.put(Recording.FORMAT_KEY, (String) jsonfile.get(Recording.FORMAT_KEY));
				
				metadata.putAll(item.getAllTagMapStrs());
			}
			
//			String languages = "";
//			joiner = "";
//			for (Object obj: (JSONArray) jsonfile.get(Recording.LANGUAGES_KEY)) {
//				String lang;
//				if (obj instanceof JSONObject) {
//					lang = (String) ((JSONObject) obj).get("code");
//				} else {
//					lang = (String) obj;
//				}
//				languages += joiner + lang;
//				joiner = ",";
//			}
			
			metadata.put(FileModel.USER_ID_KEY, emailAddr);
			//metadata.put(FileModel.DATA_STORE_URI_KEY, uri);
			metadata.put(Recording.ITEM_ID_KEY, groupId); // This key is used for all data types
			metadata.put(Recording.FILE_TYPE_KEY, item.getFileType()); // This key is used for all data types
//			metadata.put(Recording.SPEAKERS_KEY, speakers); // Empty for other data types
//			metadata.put(Recording.LANGUAGES_KEY, languages); // This key is also used for SPEAKER_LANGUAGES
			//metadata.put("metadata", jsonstr); // Used for FusionTable before fullText-indexing
			
		} else {	// Other-type(transcript, mapping)
			metadata.put(FileModel.USER_ID_KEY, emailAddr);
			//metadata.put(FileModel.DATA_STORE_URI_KEY, uri);
			
			String groupId = splitName[0];
			metadata.put(Recording.ITEM_ID_KEY, groupId);
			metadata.put(Recording.FILE_TYPE_KEY, item.getFileType());
//			metadata.put(Recording.SPEAKERS_KEY, "");
//			metadata.put(Recording.LANGUAGES_KEY, "");
		}
		
		
		// tags are used to group derivative-wave-file and mapping-file
//		String suffix = splitName[splitName.length-1];
//		if(suffix.length() == 3 && NumberUtils.isNumber(suffix)) {
//			metadata.put("tags", suffix);
//		}	
		
		boolean isSharedFile = gd.share(identifier);
		boolean isSharedMetaFile = 
				(metadataFile == null) || gd.share(item.getCloudIdentifier(1));

		boolean isIndexed = fi.index(identifier, metadata);
		boolean isIndexedMetaFile = (metadataFile == null);
		if(metadataFile != null) {
			metadata.put(Recording.FILE_TYPE_KEY, FileModel.METADATA_TYPE); // Is this necessary for search ???
			isIndexedMetaFile |= fi.index(item.getCloudIdentifier(1), metadata);
		}
				
		Log.i(TAG, "(" + isSharedFile +", " + isSharedMetaFile +", " + 
						 isIndexed + ", " + isIndexedMetaFile + "): " + metadata.toString());
		
		// TODO: These 4 steps need to be saved as a state after each step
		if (isSharedFile && isSharedMetaFile && isIndexed && isIndexedMetaFile) {
			Log.i(TAG, "Metadata-upload success");
			return uploadDate;
		}
		else {
			Log.i(TAG, "Metadata-upload failed");
			return null;
		}
	}
	
	// status(0: start, 1:new file download, 2:stop)
	private void broadcastStatus(String status) {
		Intent intent = new Intent(SYNC_RESULT);
		intent.putExtra(SYNC_STATUS, status);    
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void validateToken() {
		try {
			googleAuthToken = GoogleAuthUtil.getToken(getApplicationContext(), googleEmailAccount, AikumaSettings.getScope());
			
			googleIdToken = GoogleAuthUtil.getToken(getApplicationContext(), googleEmailAccount, AikumaSettings.getIdTokenScope());
			
		} catch (Exception e) {
			Log.e(TAG, "Unrecoverable error " + e.getMessage());
		}
	}
	
}
