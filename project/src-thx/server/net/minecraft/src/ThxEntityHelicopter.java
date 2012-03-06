package net.minecraft.src;

import java.util.List;


public class ThxEntityHelicopter extends ThxEntity implements IClientDriven, ISpawnable
{
    public ThxEntityHelicopter(World world)
    {
        super(world);
        
        setSize(1.8f, 2f);
        yOffset = .6f;
        NET_PACKET_TYPE = 75;
        log("C1 - ThxEntityHelicopter() with world: " + world.getWorldInfo());
    }

    public ThxEntityHelicopter(World world, double x, double y, double z, float yaw)
    {
        this(world);
        
        setPositionAndRotation(x, y + yOffset, z, yaw, 0f);
        log("C2 - posX: " + posX + ", posY: " + posY + ", posZ: " + posZ + ", yaw: " + yaw);
    }

    @Override
    public void onUpdate()
    {
        // adjust position height to avoid collisions
        List list = worldObj.getCollidingBoundingBoxes(this, boundingBox.contract(0.03125D, 0.0D, 0.03125D));
        if (list.size() > 0)
        {
            double d3 = 0.0D;
            for (int j = 0; j < list.size(); j++)
            {
                AxisAlignedBB axisalignedbb = (AxisAlignedBB)list.get(j);
                if (axisalignedbb.maxY > d3)
                {
                    d3 = axisalignedbb.maxY;
                }
            }

            posY += d3 - boundingBox.minY;
            setPosition(posX, posY, posZ);
        }

        super.onUpdate(); // ThxEntity.onUpdate will apply latest client packet if there is one
        
        if (riddenByEntity != null)
        {
            // entity updates will come from client for player pilot
            
            if (riddenByEntity.isDead) riddenByEntity.mountEntity(this);
            
	        moveEntity(motionX, motionY, motionZ);
	        handleCollisions();
	        
	        // fire weapons and clear flags
            if (fire1 > 0)
            {
                fire1 = 0;
                fireRocket();
            }
            if (fire2 > 0)
            {
                fire2 = 0;
                fireMissile();
            }
	        
            return;
        }
        
        // for auto-heal unattended, otherwise damage set by pilot client
        if (damage > 0f) damage -= deltaTime; // heal rate: 1 pt / sec

        onUpdateVacant();
            
        moveEntity(motionX, motionY, motionZ);
        handleCollisions();
    }
    
    protected void onUpdateVacant()
    {
        if (throttle > .001) log("throttle: " + throttle);
        
        throttle *= .6; // quickly zero throttle
        
        if (onGround || inWater)
        {
            if (Math.abs(rotationPitch) > .1f) rotationPitch *= .70f;
            if (Math.abs(rotationRoll) > .1f) rotationRoll *= .70f; // very little lateral
                
            // tend to stay put on ground
            motionY = 0.;
            motionX *= .7;
            motionZ *= .7;
                
            rotationYawSpeed = 0f;
        }
        else
        {
            // settle back to ground naturally if pilot bails
                
            rotationPitch *= PITCH_RETURN;
            rotationRoll *= ROLL_RETURN;
                
            motionX *= FRICTION;
            motionY -= GRAVITY * .16f * deltaTime / .05f;
            motionZ *= FRICTION;
        }
    }
    
    @Override
    public boolean attackEntityFrom(DamageSource damageSource, int i)
    {
        if (!super.attackEntityFrom(damageSource, i)) return false; // no hit

        takeDamage((float) i * 3f);

        timeSinceAttacked = .5f; // sec delay before this entity can be attacked again

        setBeenAttacked(); // this will cause Entity.velocityChanged to be true, so additional Packet28

        return true; // the hit landed
    }

    @Override
    public void updateRiderPosition()
    {
        if (riddenByEntity == null) return;

        // this will tell the default impl in Entity.updateRidden()
        // that no adjustment need be made to the pilot's yaw or pitch
        // as a direct result of riding this helicopter entity.
        // rather, we let the player rotate the pilot and the helicopter follows
        prevRotationYaw = rotationYaw;
        prevRotationPitch = rotationPitch;
        
        // use fwd XZ components to adjust front/back position of pilot based on helicopter pitch
        // when in 1st-person mode to improve view
        double posAdjust = 0; //-.1 + .02f * rotationPitch;

        //if (ModLoader.getMinecraftInstance().gameSettings.thirdPersonView != 0) posAdjust = 0.0;

        // to force camera to follow helicopter exactly, but stutters:
        //pilot.setPositionAndRotation(posX + fwd.x * posAdjust, posY + pilot.getYOffset() + getMountedYOffset(), posZ + fwd.z * posAdjust, rotationYaw, rotationPitch);
        //pilot.setLocationAndAngles(posX + fwd.x * posAdjust, posY -.7f, posZ + fwd.z * posAdjust, rotationYaw, rotationPitch);
        
        riddenByEntity.setPosition(posX + fwd.x * posAdjust, posY + riddenByEntity.getYOffset() + getMountedYOffset(), posZ + fwd.z * posAdjust);
    }

    @Override
    protected void pilotExit()
    {
        super.pilotExit();
        
        if (riddenByEntity == null) return;
        
        EntityPlayerMP pilot = (EntityPlayerMP) riddenByEntity;
        riddenByEntity.mountEntity(this); // riddenByEntity is now null
        
        Packet packet = new Packet39AttachEntity(pilot, null);
        List players = ModLoader.getMinecraftServerInstance().configManager.playerEntities;
        for (Object player : players)
        {
            if (player.equals(pilot)) continue; // already sent above by mountEntity call
            ((EntityPlayerMP) player).playerNetServerHandler.sendPacket(packet);
        }

        // place pilot to left of helicopter
        // (use fwd XZ perp to exit left: x = z, z = -x)
        double exitDist = 1.9;
        pilot.playerNetServerHandler.teleportTo(posX + fwd.z * exitDist, posY + pilot.getYOffset(), posZ - fwd.x * exitDist, rotationYaw, 0f);

    }
 
    static class Keyboard // no-op replacement for client-side org.lwjgl.input.Keyboard
    {
	    public static boolean isKeyDown(int key)
	    {
	        return false;
	    }
    }
    
}