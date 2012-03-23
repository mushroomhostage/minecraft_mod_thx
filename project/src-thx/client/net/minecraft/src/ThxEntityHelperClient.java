package net.minecraft.src;

import net.minecraft.client.Minecraft;

public class ThxEntityHelperClient extends ThxEntityHelper
{
    World world;
    Minecraft minecraft;
    GuiScreen guiScreen;
    
    ThxEntityHelperClient(ThxEntity entity, ThxModel model)
    {
        this.model = model;
        this.entity = entity;
        world = entity.worldObj;
        minecraft = ModLoader.getMinecraftInstance();
    }
    
    boolean isPaused()
    {
        if (!world.isRemote) // can only pause in single-player mode
        {
            if (guiScreen != minecraft.currentScreen)
            {
                // guiScreen has changed
                guiScreen = minecraft.currentScreen;

                if (guiScreen != null && guiScreen.doesGuiPauseGame())
                {
                    // log("game paused " + this);
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override
    void addChatMessage(String s)
    {
        minecraft.ingameGUI.addChatMessage(s);
    }

    void sendUpdatePacketToServer(Packet230ModLoader packet)
    {
        if (!world.isRemote) return;

        // only the pilot player can send updates to the server
        if (!minecraft.thePlayer.equals(entity.riddenByEntity)) return;

        //log("Sending update packet: " + packet);
        minecraft.getSendQueue().addToSendQueue(packet);
    }

    void applyUpdatePacket(Packet230ModLoader packet)
    {
        if (packet == null) return;
        
        //entity.plog("applyUpdatePacket: " + entity.packetToString(packet));
        
        int packetPilotId = packet.dataInt[1];
        
        // no or wrong current pilot
        if (packetPilotId > 0 && (entity.riddenByEntity == null || entity.riddenByEntity.entityId != packetPilotId))
        {
            Entity pilot = ((WorldClient) world).getEntityByID(packetPilotId);
            if (pilot != null && !pilot.isDead)
            {
                log("*** applyUpdatePacket: pilot " + pilot + " now boarding");
                pilot.mountEntity(entity);
            }
        }
        else if (packetPilotId == 0 && entity.riddenByEntity != null)
        {
            log("*** current pilot id " + entity.riddenByEntity.entityId + " is exiting");
            entity.riddenByEntity.mountEntity(entity); // unmount
        }
        
        int ownerId = packet.dataInt[4];
        if (ownerId > 0) entity.owner = ((WorldClient) world).getEntityByID(ownerId);
        //log("Entity owner: " + entity.owner);

        entity.serverPosX = MathHelper.floor_float(packet.dataFloat[0] * 32f);
        entity.serverPosY = MathHelper.floor_float(packet.dataFloat[1] * 32f);
        entity.serverPosZ = MathHelper.floor_float(packet.dataFloat[2] * 32f);
    }
}
