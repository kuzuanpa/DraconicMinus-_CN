package ru.nanolive.draconicplus.common.fusioncrafting.tiles;

import cofh.api.energy.IEnergyReceiver;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.util.ForgeDirection;
import ru.nanolive.draconicplus.common.fusioncrafting.BlockPos;
import ru.nanolive.draconicplus.common.fusioncrafting.ICraftingInjector;
import ru.nanolive.draconicplus.common.fusioncrafting.IFusionCraftingInventory;
import ru.nanolive.draconicplus.common.fusioncrafting.network.SyncableByte;
import ru.nanolive.draconicplus.common.fusioncrafting.network.SyncableLong;

/**
 * Created by brandon3055 on 10/06/2016.
 */
public class TileCraftingInjector extends TileInventoryBase implements IEnergyReceiver, ICraftingInjector, ISidedInventory {

    public final SyncableByte facing = new SyncableByte((byte)0, true, false, true);
    private final SyncableLong energy = new SyncableLong(0, true, false);
    public IFusionCraftingInventory currentCraftingInventory = null;

    public TileCraftingInjector(){
        this.setInventorySize(1);
        registerSyncableObject(facing, true);
        registerSyncableObject(energy, true);
    }

    public int ticker = 0;
    
    @Override
    public void updateEntity() {
    	if(ticker++ > 60) {
    		this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
    		ticker = 0;
    	}
    }
    
    @Override
    public void updateBlock() {
        super.updateBlock();
        detectAndSendChanges();
    }

    //region IEnergy

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        validateCraftingInventory();
        if (currentCraftingInventory != null){
            long maxRFPerTick = currentCraftingInventory.getEnergyCost() / 300;
            long maxAccept = Math.min(maxReceive, Math.min(currentCraftingInventory.getEnergyCost() - energy.value, maxRFPerTick));

            if (!simulate){
                energy.value += maxAccept;
                ticker+=20;
            }
            return (int) maxAccept;
        }
        return 0;
    }

    @Override
    public int getEnergyStored(ForgeDirection from) {
        return (int) energy.value;
    }
    public long getEnergyStored() {
        return energy.value;
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from) {
        return ((int) (currentCraftingInventory==null?0:currentCraftingInventory.getEnergyCost()));
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return from != from.getOrientation(facing.value);
    }

    //endregion

    //region ICraftingPedestal

    @Override
    public int getPedestalTier() {
    	return this.blockMetadata;
    }

    @Override
    public ItemStack getStackInPedestal() {
        return getStackInSlot(0);
    }

    @Override
    public void setStackInPedestal(ItemStack stack) {
        setInventorySlotContents(0, stack);
    }

    @Override
    public boolean setCraftingInventory(IFusionCraftingInventory craftingInventory) {
        if (validateCraftingInventory() && !worldObj.isRemote) {
            return false;
        }
        currentCraftingInventory = craftingInventory;
        return true;
    }

    @Override
    public EnumFacing getDirection() {
        return EnumFacing.getFront(facing.value);
    }

    @Override
    public long getInjectorCharge() {
        return energy.value;
    }

    @Override
    public long getEnergyRequired() {
        return currentCraftingInventory==null?0:currentCraftingInventory.getEnergyCost();
    }

    public BlockPos getPos(){
        return new BlockPos(xCoord,yCoord,zCoord);
    }

    private boolean validateCraftingInventory(){
        if (getStackInPedestal() != null && currentCraftingInventory != null && currentCraftingInventory.craftingInProgress() && !((TileEntity)currentCraftingInventory).isInvalid()){
            return true;
        }

        currentCraftingInventory = null;
        return false;
    }


    @Override
    public void onCraft() {
        if (currentCraftingInventory != null){
            energy.value = 0;
        }
    }

    //endregion

	@Override
	public int[] getAccessibleSlotsFromSide(int side) {
		return new int[]{0};
	}
    
    @Override
    public boolean canInsertItem(int index, ItemStack itemStackIn, int direction) {
        return true;
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, int direction) {
        return true;
    }
    
    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        super.setInventorySlotContents(index, stack);
        updateBlock();
    }
    
	 @Override
	 public void readFromNBT(NBTTagCompound nbttagcompound) {
	        super.readFromNBT(nbttagcompound);
			
	        facing.value = nbttagcompound.getByte("Facing");
	        blockMetadata = nbttagcompound.getInteger("Metadata");
         energy.value = nbttagcompound.getLong("Energy");

	        NBTTagList nbttaglist = nbttagcompound.getTagList("Items", 10);
	        this.inventoryStacks  = new ItemStack[getSizeInventory()];
	        for (int i = 0; i < nbttaglist.tagCount(); i++) {
	            NBTTagCompound nbttagcompound1 = nbttaglist.getCompoundTagAt(i);
	            int j = nbttagcompound1.getByte("Slot") & 0xFF;
	            if (j >= 0 && j < this.inventoryStacks .length) {
	                this.inventoryStacks[j] = ItemStack.loadItemStackFromNBT(nbttagcompound1);
	            }
	        }
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound) {
	        super.writeToNBT(nbttagcompound);
			
	        nbttagcompound.setByte("Facing", facing.value);
	        nbttagcompound.setInteger("Metadata", blockMetadata);
        nbttagcompound.setLong("Energy", energy.value);

	        NBTTagList nbttaglist = new NBTTagList();
	        for (int i = 0; i < this.inventoryStacks.length; i++) {
	            if (this.inventoryStacks[i] != null) {
	                NBTTagCompound nbttagcompound1 = new NBTTagCompound();
	                nbttagcompound1.setByte("Slot", (byte)i);
	                this.inventoryStacks[i].writeToNBT(nbttagcompound1);
	                nbttaglist.appendTag(nbttagcompound1);
	            }
	        }
	        nbttagcompound.setTag("Items", nbttaglist);
	}

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbttagcompound = new NBTTagCompound();
        writeToNBT(nbttagcompound);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbttagcompound);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
    }
}