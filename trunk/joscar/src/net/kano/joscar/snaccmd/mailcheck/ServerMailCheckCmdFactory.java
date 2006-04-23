package net.kano.joscar.snaccmd.mailcheck;

import net.kano.joscar.snac.SnacCmdFactory;
import net.kano.joscar.snac.CmdType;
import net.kano.joscar.DefensiveTools;
import net.kano.joscar.flapcmd.SnacCommand;
import net.kano.joscar.flapcmd.SnacPacket;

import java.util.List;

/**
 * A SNAC command factory for the client-bound SNAC commands provided in this
 * package, appropriate for use in an AIM client.
 */
public class ServerMailCheckCmdFactory implements SnacCmdFactory {
    /** The supported SNAC command types. */
    protected static final List<CmdType> SUPPORTED_TYPES = DefensiveTools.asUnmodifiableList(
        new CmdType(MailCheckCmd.FAMILY_MAILCHECK, MailCheckCmd.CMD_MAIL_REQUEST),
        new CmdType(MailCheckCmd.FAMILY_MAILCHECK, MailCheckCmd.CMD_ACTIVATE));

    public List<CmdType> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    public SnacCommand genSnacCommand(SnacPacket packet) {
        if (packet.getFamily() != MailCheckCmd.FAMILY_MAILCHECK) return null;

        int command = packet.getCommand();

        if (command == MailCheckCmd.CMD_MAIL_REQUEST) {
            return new MailStatusRequest(packet);
        } else if (command == MailCheckCmd.CMD_ACTIVATE) {
            return new ActivateMailCmd(packet);
        } else {
            return null;
        }
    }
}
