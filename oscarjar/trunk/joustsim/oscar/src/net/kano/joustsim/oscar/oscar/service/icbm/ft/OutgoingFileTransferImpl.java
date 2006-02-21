/*
 *  Copyright (c) 2005, The Joust Project
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *  - Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *  - Neither the name of the Joust Project nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 *
 */

package net.kano.joustsim.oscar.oscar.service.icbm.ft;

import net.kano.joscar.DefensiveTools;
import net.kano.joscar.rv.RvSession;
import net.kano.joscar.rvcmd.InvitationMessage;
import net.kano.joscar.rvcmd.SegmentedFilename;
import net.kano.joscar.rvcmd.sendfile.FileSendBlock;
import static net.kano.joscar.rvcmd.sendfile.FileSendBlock.SENDTYPE_DIR;
import static net.kano.joscar.rvcmd.sendfile.FileSendBlock.SENDTYPE_SINGLEFILE;
import net.kano.joustsim.Screenname;
import net.kano.joustsim.oscar.oscar.service.icbm.dim.MutableSessionConnectionInfo;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.ChecksumController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.SendFileController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.SendOverProxyController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.SendPassivelyController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.StateController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.TransferredFile;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.TransferredFileImpl;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.controllers.ConnectedController;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.ChecksummingEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.ConnectionCompleteEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.RvConnectionEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.events.UnknownErrorEvent;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.state.ComputedChecksumsInfo;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.state.FailedStateInfo;
import net.kano.joustsim.oscar.oscar.service.icbm.ft.state.StateInfo;
import net.kano.joustsim.oscar.proxy.AimProxyInfo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OutgoingFileTransferImpl
    extends OutgoingRvConnectionImpl implements OutgoingFileTransfer {
  private String folderName;
  private Map<TransferredFile, Long> checksums = new HashMap<TransferredFile, Long>();
  private FileChecksummer fileChecksummer = new FileChecksummerImpl();
  private final FileTransferHelper helper = new FileTransferHelper(this);
  @SuppressWarnings({"unchecked"})
  private List<TransferredFile> tfiles = Collections.EMPTY_LIST;

  public OutgoingFileTransferImpl(AimProxyInfo proxy,
      Screenname screenname, RvSessionConnectionInfo rvsessioninfo) {
    super(proxy, screenname, rvsessioninfo);
  }

  public OutgoingFileTransferImpl(AimProxyInfo proxy, Screenname screenname,
      RvSession session) {
    super(proxy, screenname, new MutableSessionConnectionInfo(session));
    ((MutableSessionConnectionInfo) getRvSessionInfo())
        .setMaker(new FileTransferRequestMaker(this));
  }

  public void sendRequest(InvitationMessage msg) {
    setInvitationMessage(msg);
    startStateController(new ChecksumController());
  }

  public synchronized String getFolderName() { return folderName; }

  @SuppressWarnings({"ReturnOfCollectionOrArrayField"})
  public synchronized List<TransferredFile> getFiles() {
    return tfiles;
  }

  public synchronized void setFile(File file) throws IOException {
    setFile(file, file.getName());
  }

  public synchronized void setFile(File file, String name) throws IOException {
    DefensiveTools.checkNull(file, "file");
    DefensiveTools.checkNull(name, "name");

    folderName = null;
    tfiles = Collections.<TransferredFile>singletonList(
        new TransferredFileImpl(file, name, "r"));
  }

  public synchronized void setFiles(String folderName, List<File> files)
      throws IOException {
    DefensiveTools.checkNull(folderName, "folderName");
    DefensiveTools.checkNullElements(files, "files");

    List<TransferredFile> tfiles = new ArrayList<TransferredFile>(files.size());
    for (File file : files) {
      tfiles.add(new TransferredFileImpl(file, folderName
          + SegmentedFilename.FILESEP_NATIVE + file.getName(), "r"));
    }

    setFilesWithDetails(folderName, tfiles);
  }

  public synchronized void setFilesWithDetails(String folderName,
      List<TransferredFile> files) {
    DefensiveTools.checkNullElements(files, "files");

    this.folderName = folderName;
    this.tfiles = DefensiveTools.getUnmodifiableCopy(tfiles);
  }

  public FileChecksummer getChecksummer() { return fileChecksummer; }

  public FileSendBlock getFileInfo() {
    long totalSize = 0;
    List<TransferredFile> files = getFiles();
    for (TransferredFile file : files) totalSize += file.getSize();
    int numFiles = files.size();
    boolean folderMode = numFiles > 1;
    int sendType;
    String filename;
    if (folderMode) {
      sendType = SENDTYPE_DIR;
      filename = getFolderName();

    } else {
      assert numFiles == 1;
      sendType = SENDTYPE_SINGLEFILE;
      filename = files.get(0).getTransferredName();
    }
    return new FileSendBlock(sendType, filename, numFiles, totalSize);
  }

  protected StateController getNextControllerFromUnknownError(
      StateController oldController, FailedStateInfo failedStateInfo,
      RvConnectionEvent event) {
    if (oldController instanceof SendFileController) {
      //TODO: retry send with other controllers like receiver does
//                if (getState() == FileTransferState.TRANSFERRING) {
      queueStateChange(RvConnectionState.FAILED,
          event == null ? new UnknownErrorEvent() : event);
//                } else {
//
//                }
      return null;

    } else {
      throw new IllegalStateException("unknown previous controller "
          + oldController);
    }
  }

  protected StateController getNextControllerFromSuccess(
      StateController oldController, StateInfo endState) {
    if (oldController instanceof SendFileController) {
      queueStateChange(RvConnectionState.FINISHED,
          new ConnectionCompleteEvent());
      return null;

    } else if (oldController instanceof ChecksumController) {
      if (endState instanceof ComputedChecksumsInfo) {
        ComputedChecksumsInfo info = (ComputedChecksumsInfo) endState;
        checksums.putAll(info.getChecksums());
      }
      if (getSettings().isOnlyUsingProxy()) {
        return new SendOverProxyController();
      } else {
        return new SendPassivelyController();
      }

    } else {
      throw new IllegalStateException("unknown previous controller "
          + oldController);
    }
  }

  protected ConnectedController createConnectedController(StateInfo endState) {
    return new SendFileController();
  }

  protected boolean isConnectedController(StateController controller) {
    return controller instanceof SendFileController;
  }

  public RvRequestMaker getRvRequestMaker() {
    return helper.getRvRequestMaker();
  }

  public InvitationMessage getInvitationMessage() {
    return helper.getInvitationMessage();
  }

  private void setInvitationMessage(InvitationMessage msg) {
    helper.setInvitationMessage(msg);
  }

  public void sendRequest() {
    sendRequest(null);
  }

  private class FileChecksummerImpl implements FileChecksummer {
    public long getChecksum(TransferredFile mfile) throws IOException {
      Long sum = checksums.get(mfile);
      if (sum == null) {
        RandomAccessFile raf = new RandomAccessFile(mfile.getRealFile(), "r");
        Checksummer summer = new ChecksummerImpl(raf.getChannel(), raf.length());
        fireEvent(new ChecksummingEvent(mfile, summer));
        sum = summer.compute();
      }
      return sum;
    }
  }
}