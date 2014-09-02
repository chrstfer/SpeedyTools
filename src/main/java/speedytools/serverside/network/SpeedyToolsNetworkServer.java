//package speedytools.serverside.network;
//
//import cpw.mods.fml.common.IPlayerTracker;
//import cpw.mods.fml.common.registry.GameRegistry;
//import cpw.mods.fml.relauncher.Side;
//import net.minecraft.entity.player.EntityPlayer;
//import net.minecraft.entity.player.EntityPlayerMP;
//import net.minecraft.network.packet.Packet250CustomPayload;
//import speedytools.common.network.Packet250CloneToolAcknowledge.Acknowledgement;
//import speedytools.common.network.*;
//import speedytools.common.utilities.ErrorLog;
//import speedytools.common.utilities.ResultWithReason;
//import speedytools.serverside.ServerSide;
//import speedytools.serverside.actions.SpeedyToolServerActions;
//
//import java.util.HashMap;
//import java.util.Map;
//
///**
// * User: The Grey Ghost
// * Date: 8/03/14
// * Used to receive commands from the client and send status messages back.  See networkprotocols.txt
// * Usage:
// * (1) addPlayer when player joins, removePlayer when player leaves
// * (2) changeServerStatus to let all interested clients know what the server is doing (busy or not)
// * (3) handlePacket should be called to process incoming packets from the client
// * (4) in response to an incoming ToolAction, will call SpeedyToolServerActions.performToolAction.
// *     performToolAction must:
// *       a) return true/false depending on whether the action is accepted or not
// *       b) update changeServerStatus
// *       c) when the action is finished, call actionCompleted and update changeServerStatus
// * (5) in response to an incoming Undo, will call SpeedyToolServerActions.performUndoOfLastAction (if the client is undoing
// *        an action that has been completed and acknowledged), or .performUndoOfCurrentAction (if the client is undoing the
// *          action currently being performed, i.e. hasn't received a completed acknowledgement yet)
// *        The SpeedyToolServerActions must:
// *       a) return true/false depending on whether the undo is accepted or not
// *       b) update changeServerStatus
// *       c) when the undo is finished, call undoCompleted.  actionCompleted should have been sent first.
// * (6) tick() must be called at frequent intervals to check for timeouts - at least once per second
// */
//
//public class SpeedyToolsNetworkServer
//{
//  public SpeedyToolsNetworkServer(PacketHandlerRegistry packetHandlerRegistry, SpeedyToolServerActions i_speedyToolServerActions)
//  {
//    playerStatuses = new HashMap<EntityPlayerMP, ClientStatus>();
//    lastAcknowledgedAction = new HashMap<EntityPlayerMP, Integer>();
//    lastAcknowledgedActionPacket = new HashMap<EntityPlayerMP, Packet250CustomPayload>();
//    lastAcknowledgedUndo = new HashMap<EntityPlayerMP, Integer>();
//    lastAcknowledgedUndoPacket = new HashMap<EntityPlayerMP, Packet250CustomPayload>();
//    lastStatusPacketTimeNS = new HashMap<EntityPlayerMP, Long>();
// //   undoNotifications = new HashMap<EntityPlayerMP, ArrayList<TimeStampSequenceNumber>>();
//
//    speedyToolServerActions = i_speedyToolServerActions;
//    speedyToolServerActions.setCloneToolsNetworkServer(this);
//
//    packetHandlerCloneToolUse = this.new PacketHandlerCloneToolUse();
//    packetHandlerRegistry.registerHandlerMethod(Side.SERVER, Packet250Types.PACKET250_CLONE_TOOL_USE_ID.getPacketTypeID(), packetHandlerCloneToolUse);
//    packetHandlerCloneToolStatus = this.new PacketHandlerCloneToolStatus();
//    packetHandlerRegistry.registerHandlerMethod(Side.SERVER, Packet250Types.PACKET250_TOOL_STATUS_ID.getPacketTypeID(), packetHandlerCloneToolStatus);
//
//    packetHandlerSpeedyToolUse = this.new PacketHandlerSpeedyToolUse();
//    packetHandlerRegistry.registerHandlerMethod(Side.SERVER, Packet250Types.PACKET250_SPEEDY_TOOL_USE_ID.getPacketTypeID(), packetHandlerSpeedyToolUse);
//
//    playerTracker = this.new PlayerTracker();
//    GameRegistry.registerPlayerTracker(playerTracker);
//  }
//
//  public void addPlayer(EntityPlayerMP newPlayer)
//  {
//    if (!playerStatuses.containsKey(newPlayer)) {
//      playerStatuses.put(newPlayer, ClientStatus.IDLE);
//      lastAcknowledgedAction.put(newPlayer, Integer.MIN_VALUE);
//      lastAcknowledgedUndo.put(newPlayer, Integer.MIN_VALUE);
//      lastStatusPacketTimeNS.put(newPlayer, new Long(0));
//    }
//  }
//
//  public void removePlayer(EntityPlayerMP whichPlayer)
//  {
//    playerStatuses.remove(whichPlayer);
//    lastAcknowledgedAction.remove(whichPlayer);
//    lastAcknowledgedUndo.remove(whichPlayer);
//    lastAcknowledgedUndoPacket.remove(whichPlayer);
//    lastAcknowledgedActionPacket.remove(whichPlayer);
//    lastStatusPacketTimeNS.remove(whichPlayer);
//  }
//
//  /**
//   * Changes the server status and informs all clients who are interested in it.
//   * @param newServerStatus
//   * @param newServerPercentComplete
//   */
//  public void changeServerStatus(ServerStatus newServerStatus, EntityPlayerMP newPlayerBeingServiced, byte newServerPercentComplete)
//  {
//    assert (newServerPercentComplete >= 0 && newServerPercentComplete <= 100);
//    if (newServerStatus == serverStatus && newPlayerBeingServiced == playerBeingServiced && newServerPercentComplete == serverPercentComplete) {
//      return;
//    }
//
//    serverStatus = newServerStatus;
//    serverPercentComplete = newServerPercentComplete;
//    playerBeingServiced = newPlayerBeingServiced;
//    for (Map.Entry<EntityPlayerMP,ClientStatus> playerStatus : playerStatuses.entrySet()) {
//      if (playerStatus.getValue() != ClientStatus.IDLE) {
//        sendUpdateToClient(playerStatus.getKey());
//      }
//    }
//  }
//
//  /** tell the client that the current action has been completed
//   *
//   * @param player
//   * @param actionSequenceNumber
//   */
//  public void actionCompleted(EntityPlayerMP player, int actionSequenceNumber)
//  {
//    sendAcknowledgement(player, Acknowledgement.COMPLETED, actionSequenceNumber, Acknowledgement.NOUPDATE, 0);
//  }
//
//  /**tell the client that the current undo has been completed
//   *
//   * @param player
//   * @param undoSequenceNumber
//   */
//  public void undoCompleted(EntityPlayerMP player, int undoSequenceNumber)
//  {
//    sendAcknowledgement(player, Acknowledgement.NOUPDATE, 0, Acknowledgement.COMPLETED, undoSequenceNumber);
//  }
//
//  /**
//   * Send the appropriate update status packet to this player
//   * @param player
//   */
//  private void sendUpdateToClient(EntityPlayerMP player)
//  {
//    ServerStatus serverStatusForThisPlayer = serverStatus;
//    String nameOfOtherPlayerBeingServiced = "";
//    if (player != playerBeingServiced) {
//      switch (serverStatus) {
//        case IDLE:
//        case PERFORMING_BACKUP: {
//          break;
//        }
//        case PERFORMING_YOUR_ACTION:
//        case UNDOING_YOUR_ACTION: {
//          serverStatusForThisPlayer = ServerStatus.BUSY_WITH_OTHER_PLAYER;
//          nameOfOtherPlayerBeingServiced = (playerBeingServiced == null) ? "someone" : playerBeingServiced.getDisplayName();
//          break;
//        }
//        default:
//          assert false: "Invalid serverStatus";
//      }
//    }
//
//    Packet250CloneToolStatus packet = Packet250CloneToolStatus.serverStatusChange(serverStatusForThisPlayer, serverPercentComplete, nameOfOtherPlayerBeingServiced);
//    Packet250CustomPayload packet250 = packet.getPacket250CustomPayload();
//    if (packet250 != null) {
//      player.playerNetServerHandler.sendPacketToPlayer(packet250);
//      lastStatusPacketTimeNS.put(player, System.nanoTime());
//    }
//  }
//
//  /**
//   * sends a packet back to the client acknowledging their Action or undo, with success or failure
//   * @param player
//   * @param actionAcknowledgement
//   * @param actionSequenceNumber
//   * @param undoAcknowledgement
//   * @param undoSequenceNumber
//   * @param reason if the action or undo is rejected, a human-readable message can be provided.  "" for none.
//   */
//  private void sendAcknowledgementWithReason(EntityPlayerMP player,
//                                             Acknowledgement actionAcknowledgement, int actionSequenceNumber,
//                                             Acknowledgement undoAcknowledgement, int undoSequenceNumber,
//                                             String reason)
//  {
//    sendAcknowledgement_do(player, actionAcknowledgement, actionSequenceNumber, undoAcknowledgement, undoSequenceNumber, reason);
//  }
//
//  /**
//   *  send a packet back to the client acknowledging their Action, with success or failure
//   *  updates private variables to reflect the latest action and/or undo acknowledgments
//   */
//  private void sendAcknowledgement(EntityPlayerMP player,
//                                   Acknowledgement actionAcknowledgement, int actionSequenceNumber,
//                                   Acknowledgement undoAcknowledgement, int undoSequenceNumber       )
//  {
//    sendAcknowledgement_do(player, actionAcknowledgement, actionSequenceNumber, undoAcknowledgement, undoSequenceNumber, "");
//  }
//  /**
//   *  send a packet back to the client acknowledging their Action, with success or failure
//   *  updates private variables to reflect the latest action and/or undo acknowledgments
//   */
//  private void sendAcknowledgement_do(EntityPlayerMP player,
//                                      Acknowledgement actionAcknowledgement, int actionSequenceNumber,
//                                      Acknowledgement undoAcknowledgement, int undoSequenceNumber,
//                                      String reason)
//  {
//    Packet250CloneToolAcknowledge packetAck;
//    packetAck =  new Packet250CloneToolAcknowledge(actionAcknowledgement, actionSequenceNumber,
//                                                   undoAcknowledgement, undoSequenceNumber, reason);
//    Packet250CustomPayload packet250 = packetAck.getPacket250CustomPayload();
//    if (packet250 != null) {
//      player.playerNetServerHandler.sendPacketToPlayer(packet250);
//    }
//
//    // verify that our packets don't contradict anything we have sent earlier
//    if (actionAcknowledgement != Acknowledgement.NOUPDATE) {
//      assert (lastAcknowledgedAction.get(player) < actionSequenceNumber ||
//              (lastAcknowledgedAction.get(player) == actionSequenceNumber
//                      && actionAcknowledgement == Acknowledgement.COMPLETED
//                      && Packet250CloneToolAcknowledge.createPacket250CloneToolAcknowledge(lastAcknowledgedActionPacket.get(player)).getActionAcknowledgement() == Acknowledgement.ACCEPTED)  );
//      lastAcknowledgedAction.put(player, actionSequenceNumber);
//      lastAcknowledgedActionPacket.put(player, packet250);
//    }
//    if (undoAcknowledgement != Acknowledgement.NOUPDATE) {
//      assert (lastAcknowledgedUndo.get(player) < undoSequenceNumber ||
//              (lastAcknowledgedUndo.get(player) == undoSequenceNumber
//                      && undoAcknowledgement == Acknowledgement.COMPLETED
//                      && Packet250CloneToolAcknowledge.createPacket250CloneToolAcknowledge(lastAcknowledgedUndoPacket.get(player)).getUndoAcknowledgement() == Acknowledgement.ACCEPTED)  );
//      lastAcknowledgedUndo.put(player, undoSequenceNumber);
//      lastAcknowledgedUndoPacket.put(player, packet250);
//    }
//  }
//  /**
//   * respond to an incoming action packet.
//   * @param player
//   * @param packet
//   */
//  public void handlePacket(EntityPlayerMP player, Packet250CloneToolUse packet)
//  {
//    System.out.println("SpeedyToolsNetworkServer.handlePacket:" + packet.getCommand());
//    switch (packet.getCommand()) {
//      case SELECTION_MADE: {
//        speedyToolServerActions.prepareForToolAction(player);
//        break;
//      }
//
//      // check against previous actions before we implement this action
//      case PERFORM_TOOL_ACTION: {
//        int sequenceNumber = packet.getSequenceNumber();
//        if (sequenceNumber == lastAcknowledgedAction.get(player)) {    // same as the action we've already acknowledged; send ack again
//          Packet250CustomPayload packet250 = lastAcknowledgedActionPacket.get(player);
//          if (packet250 != null) {
//            player.playerNetServerHandler.sendPacketToPlayer(packet250);
//          }
//          break;
//        } else if (sequenceNumber < lastAcknowledgedAction.get(player)) { // old packet, ignore
//          break; // do nothing, just ignore it
//        } else {
////          boolean foundundo = false;
////          for (TimeStampSequenceNumber tssn : undoNotifications.get(player)) {
////            if (tssn.sequenceNumber == sequenceNumber) {
////              foundundo = true;
////              break;
////            }
////          }
////          if (!foundundo) {
//          ResultWithReason result = ResultWithReason.failure();
//          if (serverStatus == ServerStatus.IDLE) {
//            result = speedyToolServerActions.performComplexAction(player, sequenceNumber, packet.getToolID(), packet.getXpos(), packet.getYpos(), packet.getZpos(),
//                                                                  packet.getQuadOrientation());
//          } else {
//            switch (serverStatus) {
//              case PERFORMING_BACKUP: {
//                result = ResultWithReason.failure("Must wait for world backup");
//                break;
//              }
//              case PERFORMING_YOUR_ACTION:
//              case UNDOING_YOUR_ACTION: {
//                if (player == playerBeingServiced) {
//                  if (serverStatus == ServerStatus.PERFORMING_YOUR_ACTION) {
//                    result = ResultWithReason.failure("Must wait for your earlier spell to finish");
//                  } else {
//                    result = ResultWithReason.failure("Must wait for your earlier spell to undo");
//                  }
//                } else {
//                  String playerName = "someone";
//                  if (playerBeingServiced != null) {
//                    playerName = playerBeingServiced.getDisplayName();
//                  }
//                  result = ResultWithReason.failure("Must wait for " + playerName + " to finish");
//                }
//                break;
//              }
//              default:
//                assert false: "Invalid serverStatus";
//            }
//          }
//          sendAcknowledgementWithReason(player, (result.succeeded() ? Acknowledgement.ACCEPTED : Acknowledgement.REJECTED), sequenceNumber, Acknowledgement.NOUPDATE, 0, result.getReason());
////          if (result.succeeded()) {
////            actionCompleted(player, sequenceNumber);             // todo: later - remove this when async
////          }
//        }
//        break;
//      }
//      case PERFORM_TOOL_UNDO: {
//        int sequenceNumber = packet.getSequenceNumber();
//        if (sequenceNumber == lastAcknowledgedUndo.get(player)) {                         // if same as last undo sent, just resend again
//          Packet250CustomPayload packet250 = lastAcknowledgedUndoPacket.get(player);
//          if (packet250 != null) {
//            player.playerNetServerHandler.sendPacketToPlayer(packet250);
//          }
//          break;
//        } else if (sequenceNumber < lastAcknowledgedUndo.get(player)) {     // old packet
//          break; // do nothing, just ignore it
//        } else {
//          ResultWithReason result = ResultWithReason.failure();
//          if (packet.getActionToBeUndoneSequenceNumber() == null) { // undo last completed action
//            if (serverStatus == ServerStatus.IDLE) {
//              result = speedyToolServerActions.performUndoOfLastComplexAction(player, packet.getSequenceNumber());
//            } else {
//              switch (serverStatus) {
//                case PERFORMING_BACKUP: {
//                  result = ResultWithReason.failure("Must wait for world backup");
//                  break;
//                }
//                case PERFORMING_YOUR_ACTION:
//                case UNDOING_YOUR_ACTION: {
//                  if (player == playerBeingServiced) {
//                    if (serverStatus == ServerStatus.PERFORMING_YOUR_ACTION) {
//                      result = ResultWithReason.failure("Must wait for your earlier spell to finish");
//                    } else {
//                      result = ResultWithReason.failure("Must wait for your earlier spell to undo");
//                    }
//                  } else {
//                    String playerName = "someone";
//                    if (playerBeingServiced != null) {
//                      playerName = playerBeingServiced.getDisplayName();
//                    }
//                    result = ResultWithReason.failure("Must wait for " + playerName + " to finish");
//                  }
//                  break;
//                }
//                default:
//                  assert false : "Invalid serverStatus";
//              }
//            }
//            sendAcknowledgementWithReason(player, Acknowledgement.NOUPDATE, 0, (result.succeeded() ? Acknowledgement.ACCEPTED : Acknowledgement.REJECTED), sequenceNumber, result.getReason());
////            if (result.succeeded()) {
////              undoCompleted(player, sequenceNumber);             // todo: later - remove this when async
////            }
//            break;
//          } else if (packet.getActionToBeUndoneSequenceNumber() > lastAcknowledgedAction.get(player)    ) {  // undo for an action we haven't received yet
//            sendAcknowledgementWithReason(player, Acknowledgement.REJECTED, packet.getActionToBeUndoneSequenceNumber(),
//                                          Acknowledgement.COMPLETED, packet.getSequenceNumber(),
//                                          "");
//            break;
//          } else if (packet.getActionToBeUndoneSequenceNumber() == lastAcknowledgedAction.get(player)) {    // undo for a specific action we have acknowledged as starting
//            result = speedyToolServerActions.performUndoOfCurrentComplexAction(player, packet.getSequenceNumber(), packet.getActionToBeUndoneSequenceNumber());
////            sendAcknowledgementWithReason(player, Acknowledgement.NOUPDATE, 0, (result.succeeded() ? Acknowledgement.ACCEPTED : Acknowledgement.REJECTED), sequenceNumber, result.getReason());
//            sendAcknowledgementWithReason(player, Acknowledgement.COMPLETED, lastAcknowledgedAction.get(player),
//                    (result.succeeded() ? Acknowledgement.ACCEPTED : Acknowledgement.REJECTED), sequenceNumber, result.getReason());
//          }
//        }
//        break;
//      }
//      default: {
//        assert false: "Invalid server side packet";
//      }
//    }
//  }
//
//  /**
//   * update the status of the appropriate client; replies with the server status if the client is interested
//   * @param player
//   * @param packet
//   */
//  public void handlePacket(EntityPlayerMP player, Packet250CloneToolStatus packet)
//  {
//    ClientStatus newStatus = packet.getClientStatus();
//
//    if (!playerStatuses.containsKey(player)) {
//      ErrorLog.defaultLog().warning("SpeedyToolsNetworkServer:: Packet received from player not in playerStatuses");
//      return;
//    }
//    playerStatuses.put(player, newStatus);
//
//    if (newStatus != ClientStatus.IDLE) {
//      sendUpdateToClient(player);
//    }
//
//  }
//
//  /**
//   * sends periodic status updates to the clients who have registered an interest.
//   */
//  public void tick()
//  {
//    long thresholdTime = System.nanoTime() - STATUS_UPDATE_WAIT_TIME_MS * 1000 * 1000;
//    for (Map.Entry<EntityPlayerMP, ClientStatus> clientStatus : playerStatuses.entrySet()) {
//      ServerSide.getInGameStatusSimulator().setTestMode(clientStatus.getKey());  // for in-game testing purposes
//      if (ServerSide.getInGameStatusSimulator().isTestModeActivated()) {
//        serverStatus = ServerSide.getInGameStatusSimulator().getForcedStatus(serverStatus, Side.CLIENT);
//        playerBeingServiced = ServerSide.getInGameStatusSimulator().getForcedPlayerBeingServiced(playerBeingServiced, clientStatus.getKey(), Side.CLIENT);
//        serverPercentComplete = ServerSide.getInGameStatusSimulator().getForcedPercentComplete(serverPercentComplete, Side.CLIENT);
//      }
//      if (clientStatus.getValue() != ClientStatus.IDLE) {
//        if (lastStatusPacketTimeNS.get(clientStatus.getKey()) < thresholdTime ) {
//          sendUpdateToClient(clientStatus.getKey());
//        }
//      }
//      if (ServerSide.getInGameStatusSimulator().isTestModeActivated()) {
//        serverStatus = ServerSide.getInGameStatusSimulator().getForcedStatus(serverStatus, Side.SERVER);
//        playerBeingServiced = ServerSide.getInGameStatusSimulator().getForcedPlayerBeingServiced(playerBeingServiced, clientStatus.getKey(), Side.SERVER);
//        serverPercentComplete = ServerSide.getInGameStatusSimulator().getForcedPercentComplete(serverPercentComplete, Side.SERVER);
//      }
//    }
//  }
//
//  public class PacketHandlerCloneToolUse implements PacketHandlerRegistry.PacketHandlerMethod {
//    public boolean handlePacket(EntityPlayer player, Packet250CustomPayload packet)
//    {
//      Packet250CloneToolUse toolUsePacket = Packet250CloneToolUse.createPacket250CloneToolUse(packet);
//      if (toolUsePacket == null || !toolUsePacket.validForSide(Side.SERVER)) return false;
//      SpeedyToolsNetworkServer.this.handlePacket((EntityPlayerMP) player, toolUsePacket);
//      return true;
//    }
//  }
//
//  public class PacketHandlerCloneToolStatus implements PacketHandlerRegistry.PacketHandlerMethod {
//    public boolean handlePacket(EntityPlayer player, Packet250CustomPayload packet)
//    {
//      Packet250CloneToolStatus toolStatusPacket = Packet250CloneToolStatus.createPacket250ToolStatus(packet);
//      if (toolStatusPacket == null || !toolStatusPacket.validForSide(Side.SERVER)) return false;
//      SpeedyToolsNetworkServer.this.handlePacket((EntityPlayerMP) player, toolStatusPacket);
//      return true;
//    }
//  }
//
//  public class PacketHandlerSpeedyToolUse implements PacketHandlerRegistry.PacketHandlerMethod {
//    public boolean handlePacket(EntityPlayer player, Packet250CustomPayload packet)
//    {
//      Packet250SpeedyToolUse toolUsePacket = Packet250SpeedyToolUse.createPacket250SpeedyToolUse(packet);
//      if (toolUsePacket == null) return false;
//      SpeedyToolsNetworkServer.this.speedyToolServerActions.performSimpleAction((EntityPlayerMP) player, toolUsePacket.getToolItemID(), toolUsePacket.getButton(),
//              toolUsePacket.getBlockToPlace(), toolUsePacket.getCurrentlySelectedBlocks());
//      return true;
//    }
//  }
//
//  private PacketHandlerSpeedyToolUse packetHandlerSpeedyToolUse;
//
//
//  private class PlayerTracker implements IPlayerTracker
//  {
//    public void onPlayerLogin(EntityPlayer player)
//    {
//      EntityPlayerMP entityPlayerMP = (EntityPlayerMP)player;
//      SpeedyToolsNetworkServer.this.addPlayer(entityPlayerMP);
//    }
//    public void onPlayerLogout(EntityPlayer player)
//    {
//      EntityPlayerMP entityPlayerMP = (EntityPlayerMP)player;
//      SpeedyToolsNetworkServer.this.removePlayer(entityPlayerMP);
//    }
//    public void onPlayerChangedDimension(EntityPlayer player) {}
//    public void onPlayerRespawn(EntityPlayer player) {}
//  }
//
//  private PlayerTracker playerTracker;
//
//  private Map<EntityPlayerMP, ClientStatus> playerStatuses;
//  private Map<EntityPlayerMP, Integer> lastAcknowledgedAction;
//  private Map<EntityPlayerMP, Packet250CustomPayload> lastAcknowledgedActionPacket;
//  private Map<EntityPlayerMP, Integer> lastAcknowledgedUndo;
//  private Map<EntityPlayerMP, Packet250CustomPayload> lastAcknowledgedUndoPacket;
//  private Map<EntityPlayerMP, Long> lastStatusPacketTimeNS;
//
////  private class TimeStampSequenceNumber {
////    public long timestamp;
////    public int sequenceNumber;
////  }
//
////  private Map<EntityPlayerMP, ArrayList<TimeStampSequenceNumber>> undoNotifications;
//
//  private static final int STATUS_UPDATE_WAIT_TIME_MS = 1000;  // how often to send a status update
//
//  private ServerStatus serverStatus = ServerStatus.IDLE;
//  private byte serverPercentComplete = 0;
//  private SpeedyToolServerActions speedyToolServerActions;
//  private EntityPlayerMP playerBeingServiced;
//  private PacketHandlerCloneToolUse packetHandlerCloneToolUse;
//  private PacketHandlerCloneToolStatus packetHandlerCloneToolStatus;
//
//}
