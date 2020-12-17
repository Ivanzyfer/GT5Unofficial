package gregtech.common.tileentities.machines.multi;

import gregtech.api.GregTech_API;
import gregtech.api.enums.ConfigCategories;
import gregtech.api.enums.Textures;
import gregtech.api.gui.GT_GUIContainer_MultiMachine;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.GT_MetaTileEntity_BasicHull;
import gregtech.api.metatileentity.implementations.GT_MetaTileEntity_BasicMachine_GT_Recipe;
import gregtech.api.metatileentity.implementations.GT_MetaTileEntity_MultiBlockBase;
import gregtech.api.objects.GT_RenderedTexture;
import gregtech.api.util.GT_Config;
import gregtech.api.util.GT_Recipe;
import gregtech.api.util.GT_Utility;
import net.minecraft.block.Block;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;


public class GT_MetaTileEntity_Cleanroom extends GT_MetaTileEntity_MultiBlockBase {
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int openDoorsCount = 0;
    private static int euPerVent;
    private static int idleEnergyReduceMultiplier;
    private static int cleanBlockTimeByVentTicks;
    private static int mHullsLessInTimesThanWallsSquare;
    // data for scanner:
    private int energyConsumptionMin = 0;
    private int energyConsumptionMax = 0;
    private int mHullsAllowed = 0;

    public void onConfigLoad(GT_Config aConfig){
        super.onConfigLoad(aConfig);
        euPerVent = aConfig.get(ConfigCategories.machineconfig,"Cleanroom.euPerVent",3);
        cleanBlockTimeByVentTicks = aConfig.get(ConfigCategories.machineconfig,"Cleanroom.cleanBlockTimeByVentTicks",1600);
        idleEnergyReduceMultiplier = aConfig.get(ConfigCategories.machineconfig,"Cleanroom.idleEnergyReduceMultiplier",3);
        mHullsLessInTimesThanWallsSquare = aConfig.get(ConfigCategories.machineconfig,"Cleanroom.mHullsLessInTimesThanWallsSquare",3);
    }

    public GT_MetaTileEntity_Cleanroom(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public GT_MetaTileEntity_Cleanroom(String aName) {
        super(aName);
    }

    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new GT_MetaTileEntity_Cleanroom(mName);
    }

    public String[] getDescription() {
        return new String[]{
                "Controller Block for the Cleanroom",
                "Min(WxHxD): 3x4x3 (Hollow), Max(WxHxD): 15x15x15 (Hollow)",
                "Controller (Top center)",
                "Top besides controller and edges Filter Machine Casings",
                "Blocks Plascrete for all edges",
                "1 Reinforced Door (keep closed for 100% efficency)",
                "1x Energy Hatch, the tier depends on energy consumption",
                "1x Maintenance Hatch",
                "A second door is allowed if the base area is at least 7*7",
                "Remaining Blocks Plascrete or Plascrete Window",
                String.format("Up to 1/%s of wall blocks can be replaced with Machine", mHullsLessInTimesThanWallsSquare),
                "Hulls to transfer Items & Energy through walls",
                String.format("Energy consumption depends on ceiling square, %s eu/t per block", euPerVent),
                String.format("Cleaning time depends on ceiling height, %s sec per block", (cleanBlockTimeByVentTicks / 20)),
                String.format("Energy consumption decreases after full cleaning in %s times", idleEnergyReduceMultiplier),
                "Use Portable Scanner on Controller to get more info"
        };
    }

    private int calcProgressMultiplier(int currentTier, int requiredTier){
        int multiplier = 1;
        if (currentTier <= requiredTier) {
            return multiplier;
        }
        while (currentTier > requiredTier) {
            requiredTier++;
            multiplier *= 2;
        }
        return multiplier;
    }

    public boolean checkRecipe(ItemStack aStack) {
        mMaxProgresstime = 100;
        int ceilingSquare = (sizeX-2)*(sizeZ-2);
        mEUt = euPerVent * ceilingSquare;
        energyConsumptionMax = mEUt;
        energyConsumptionMin = mEUt / idleEnergyReduceMultiplier;
        long tVoltage = getMaxInputVoltage();
        byte currentTier = (byte) Math.max(0, GT_Utility.getTier(tVoltage));
        byte requiredTier = (byte) Math.max(0, GT_Utility.getTier(mEUt));
        int progressMultiplier = calcProgressMultiplier(currentTier, requiredTier);
        int reduceEfficiencyGrowing = cleanBlockTimeByVentTicks * (sizeY-2);
        int maxEfficiencyGrowing = 1000000;
        if(progressMultiplier > 1) {
            mEfficiencyIncrease = (maxEfficiencyGrowing * progressMultiplier) / reduceEfficiencyGrowing;
            mEUt *= progressMultiplier * 2;
        } else {
            mEfficiencyIncrease = maxEfficiencyGrowing / reduceEfficiencyGrowing;
        }
        if(mEfficiency >= 10000){
            mEUt /= idleEnergyReduceMultiplier;
        }
        if (openDoorsCount > 0) {
            // set to mEfficiencyIncrease negative value, multiplied per each open door
            mEfficiencyIncrease = -(mEfficiencyIncrease * openDoorsCount);
        }

        return true;
    }

    private int calcMachinesHullAllowed(int x, int y){
        return ((x-2)*(y-2)*4-2)/mHullsLessInTimesThanWallsSquare;
    }

    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        int x = 1;
        int z = 1;
        int y = 1;
        int mDoorBlocksCount = 0;
        int mMaxDoorBlocksCount = 2; // one door contains of two blocks
        int mHullCount = 0;
        openDoorsCount = 0;
        // mUpdate = 100;

        // detect room both X and Z edge
        for (int i = 1; i < 8; i++) {
            Block tBlockX = aBaseMetaTileEntity.getBlockOffset(i, 0, 0);
            Block tBlockZ = aBaseMetaTileEntity.getBlockOffset(0, 0, i);
            int tMetaX = aBaseMetaTileEntity.getMetaIDOffset(i, 0, 0);
            int tMetaZ = aBaseMetaTileEntity.getMetaIDOffset(0, 0, i);
            if(tBlockX != GregTech_API.sBlockCasings3 || tMetaX != 11 ||
                    tBlockZ != GregTech_API.sBlockCasings3 || tMetaZ != 11){
                if(tBlockX != GregTech_API.sBlockReinforced || tMetaX != 2 ||
                        tBlockZ != GregTech_API.sBlockReinforced || tMetaZ != 2){
                    return false; // next block is not a filter and not a plascrete block
                } else {
                    x = i; z = i;
                    break; // the edge was found, cycle stopped
                }
            }
        }

        // calculate cleanroom height
        for (int i = -1; i > -16; i--) {
            Block tBlock = aBaseMetaTileEntity.getBlockOffset(x, i, z);
            int tMeta = aBaseMetaTileEntity.getMetaIDOffset(x, i, z);
            if (tBlock == GregTech_API.sBlockReinforced && tMeta == 2) {
                y = i;
            } else break; // only plascrete blocks allowed for edges
        }
        if (y > -3) {
            return false; // at least 4 blocks height allowed
        }

        sizeX = x*2+1;
        sizeY = -y+1;
        sizeZ = z*2+1;
        mHullsAllowed = calcMachinesHullAllowed(sizeX, sizeY);

        // if a base area is at least 49 a second door is allowed
        if (sizeX * sizeZ >= 49) {
            mMaxDoorBlocksCount = 4;
        }

        for (int dX = -x; dX <= x; dX++) {
            for (int dZ = -z; dZ <= z; dZ++) {
                for (int dY = 0; dY >= y; dY--) {
                    if (dX == -x || dX == x || dY == 0 || dY == y || dZ == -z || dZ == z) {
                        if (dX == 0 && dY == 0 && dZ == 0) {
                            continue; // this is controller
                        }

                        Block tBlock = aBaseMetaTileEntity.getBlockOffset(dX, dY, dZ);
                        int tMeta = aBaseMetaTileEntity.getMetaIDOffset(dX, dY, dZ);

                        if ((dX == -x || dX == x) && (dY == 0 || dY == y) ||
                                (dX == -x || dX == x) && (dZ == -z || dZ == z) ||
                                (dZ == -z || dZ == z) && (dY == 0 || dY == y)){
                            // only Plascrete Blocks allowed for edges
                            if (tBlock != GregTech_API.sBlockReinforced || tMeta != 2) {
                                return false;
                            }
                        } else if (dY == 0){ // for other blocks on the pot side except edges
                            if(tBlock != GregTech_API.sBlockCasings3 || tMeta != 11) {
                                //all blocks on the top side should be a filter blocks
                                return false;
                            }
                        } else if(tBlock == GregTech_API.sBlockReinforced && tMeta == 2) {
                            // do nothing if in other place we meet plascrete blocks
                        } else {
                            IGregTechTileEntity tTileEntity = aBaseMetaTileEntity.getIGregTechTileEntityOffset(dX, dY, dZ);
                            if ((!addMaintenanceToMachineList(tTileEntity, 82)) && (!addEnergyInputToMachineList(tTileEntity, 82))) {
                                if (tBlock instanceof ic2.core.block.BlockIC2Door) {
                                    if ((tMeta & 8) == 0) {
                                        if((Math.abs(dX) > Math.abs(dZ) == ((tMeta & 1) != 0)) != ((tMeta & 4) != 0)) {
                                            openDoorsCount++;
                                        }
                                    }
                                    mDoorBlocksCount++;
                                } else if (tBlock == GregTech_API.sBlockGlass && tMeta == 0) {
                                    // do nothing - it is Ok!
                                } else {
                                    if (tTileEntity == null) {
                                        return false;
                                    }
                                    IMetaTileEntity aMetaTileEntity = tTileEntity.getMetaTileEntity();
                                    if (aMetaTileEntity == null) {
                                        return false;
                                    }
                                    if (aMetaTileEntity instanceof GT_MetaTileEntity_BasicHull) {
                                        mHullCount++;
                                    } else {
                                        return false;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (mMaintenanceHatches.size() != 1 || mEnergyHatches.size() != 1 || mDoorBlocksCount < 2
                || mDoorBlocksCount > mMaxDoorBlocksCount || mHullCount > mHullsAllowed) {
            return false;
        }
        for (int dX = -x + 1; dX <= x - 1; dX++) {
            for (int dZ = -z + 1; dZ <= z - 1; dZ++) {
                for (int dY = -1; dY >= y + 1; dY--) {
                    IGregTechTileEntity tTileEntity = aBaseMetaTileEntity.getIGregTechTileEntityOffset(dX, dY, dZ);
                    if (tTileEntity != null) {
                        IMetaTileEntity aMetaTileEntity = tTileEntity.getMetaTileEntity();
                        if (aMetaTileEntity != null && aMetaTileEntity instanceof GT_MetaTileEntity_BasicMachine_GT_Recipe) {
                            ((GT_MetaTileEntity_BasicMachine_GT_Recipe) aMetaTileEntity).mCleanroom = this;
                        }
                    }
                }
            }
        }
        for(byte i = 0 ; i<6 ; i++){
        	byte t = (byte) Math.max(1, (byte)(15/(10000f / mEfficiency)));
            aBaseMetaTileEntity.setInternalOutputRedstoneSignal(i, t);
        }
        return true;
    }

    @Override
    public String[] getInfoData() {
        if(!mMachine) {
            return new String[]{"Incomplete Structure"};
        } else {
            int totalCleanTimeSec = (sizeY - 2) * (cleanBlockTimeByVentTicks / 20);
            float currentCleanTimeSec = (float)totalCleanTimeSec * (mEfficiency / 10000f);
            return new String[]{
                    "--Problems: " + String.valueOf(getIdealStatus() - getRepairStatus()),
                    "--Cleaning time: " + String.format("%.0f",currentCleanTimeSec) + " / " + totalCleanTimeSec,
                    "--Energy consumption (eu/t) min / max: " + energyConsumptionMin + " / " + energyConsumptionMax,
                    "--Max machine hulls allowed: " + mHullsAllowed,
                    "--Efficiency: " + (mEfficiency / 100.0F) + "%"
            };
        }
    }

    @Override
    public boolean isGivingInformation() {
        return true;
    }
    
    @Override
    public boolean allowGeneralRedstoneOutput(){
    	return true;
    }

    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, byte aSide, byte aFacing, byte aColorIndex, boolean aActive, boolean aRedstone) {
        if (aSide == 0 || aSide == 1) {
            return new ITexture[]{new GT_RenderedTexture(Textures.BlockIcons.BLOCK_PLASCRETE),
                    new GT_RenderedTexture(aActive ? Textures.BlockIcons.OVERLAY_TOP_CLEANROOM_ACTIVE : Textures.BlockIcons.OVERLAY_TOP_CLEANROOM)};

        }
        return new ITexture[]{new GT_RenderedTexture(Textures.BlockIcons.BLOCK_PLASCRETE)};
    }

    public Object getClientGUI(int aID, InventoryPlayer aPlayerInventory, IGregTechTileEntity aBaseMetaTileEntity) {
        return new GT_GUIContainer_MultiMachine(aPlayerInventory, aBaseMetaTileEntity, getLocalName(), "MultiblockDisplay.png");
    }

    public GT_Recipe.GT_Recipe_Map getRecipeMap() {
        return null;
    }

    public boolean isCorrectMachinePart(ItemStack aStack) {
        return true;
    }

    public boolean isFacingValid(byte aFacing) {
        return aFacing > 1;
    }

    public int getMaxEfficiency(ItemStack aStack) {
        return 10000;
    }

    public int getPollutionPerTick(ItemStack aStack) {
        return 0;
    }

    public int getDamageToComponent(ItemStack aStack) {
        return 0;
    }

    public int getAmountOfOutputs() {
        return 0;
    }

    public boolean explodesOnComponentBreak(ItemStack aStack) {
        return false;
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        if (!drainEnergyInput(mEUt)) {
            stopMachine();
            return false;
        }
        return true;
    }

    @Override
    public void onMachineBlockUpdate() {
        mUpdate = 15;
    }
}
