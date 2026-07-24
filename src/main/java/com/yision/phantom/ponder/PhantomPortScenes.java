package com.yision.phantom.ponder;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageStyles;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import com.simibubi.create.foundation.ponder.element.BeltItemElement;
import com.simibubi.create.infrastructure.ponder.scenes.highLogistics.PonderHilo;
import com.yision.phantom.block.phantomport.PhantomPortBlock;
import com.yision.phantom.block.phantomport.PhantomPortBlockEntity;
import com.yision.phantom.entity.courier.AirCourierEntity;
import com.yision.phantom.item.miniphantom.MiniPhantomItem;
import com.yision.phantom.logistics.courier.AirCourierHelper;
import com.yision.phantom.registry.AllItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class PhantomPortScenes {
	private static final String PLAYER_NAME = "李华";
	private static final String STATION_NAME = "Factory";
	private static final String REMOTE_ADDRESS = "Warehouse";

	private PhantomPortScenes() {}

	public static void automaticPackageTransfer(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		BlockPos beltEntryPos = util.grid().at(3, 1, 2);
		BlockPos beltEndPos = util.grid().at(1, 1, 2);
		BlockPos portPos = util.grid().at(4, 2, 2);
		BlockPos packagerPos = util.grid().at(5, 2, 2);
		BlockPos chestPos = util.grid().at(6, 2, 2);
		BlockPos linkPos = util.grid().at(5, 3, 2);
		Selection structure = util.select().everywhere();
		ItemStack cargo = new ItemStack(Items.DIAMOND);
		ItemStack outgoingPackage = addressedPackage(REMOTE_ADDRESS, cargo);
		ItemStack incomingPackage = addressedPackage(STATION_NAME, cargo);
		Vec3 beltLaunchPosition = util.vector().of(1.5, 1.875, 2.5);

		scene.title("phantom_port_automation", "Extracting and Pushing Packages");
		scene.configureBasePlate(0, 0, 8);
		scene.scaleSceneView(0.85f);
		prepareMechanics(scene, util);
		setPortAddress(scene, portPos, STATION_NAME);

		scene.world().showSection(structure, Direction.DOWN);
		scene.idle(25);

		scene.overlay().showControls(util.vector().topOf(chestPos), Pointing.DOWN, 40)
			.withItem(cargo);
		scene.idle(50);
		PonderHilo.linkEffect(scene, linkPos);
		scene.idle(10);
		PonderHilo.packagerCreate(scene, packagerPos, outgoingPackage);
		scene.idle(65);

		scene.overlay().showOutlineWithText(util.select().position(portPos), 100)
			.attachKeyFrame()
			.colored(PonderPalette.INPUT)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(portPos, Direction.NORTH))
			.text("A Phantom Port automatically extracts packages from adjacent packagers when their address differs from its name.");
		scene.idle(110);

		PonderHilo.packagerClear(scene, packagerPos);
		ElementLink<BeltItemElement> departingCourier = scene.world().createItemOnBelt(beltEntryPos, Direction.UP,
			MiniPhantomItem.createLoadedWithHeading(outgoingPackage,
				AirCourierHelper.getHeadingAngle(Direction.WEST)));
		orientBeltCourierForTravel(scene, departingCourier, Direction.WEST);
		scene.world().stallBeltItem(departingCourier, true);
		scene.effects().indicateSuccess(portPos);
		moveCourierToBeltEnd(scene, beltEntryPos, beltEndPos, departingCourier);
		ElementLink<EntityElement> outboundCourier = createCourier(scene, outgoingPackage,
			beltLaunchPosition, new Vec3(-0.15, 0.12, 0),
			AirCourierEntity.Phase.TAKEOFF, AirCourierEntity.Mission.PACKAGE_TO_AIRPORT);
		scene.idle(30);
		scene.world().modifyEntity(outboundCourier, Entity::discard);

		scene.world().hideSection(structure, Direction.UP);
		scene.idle(25);
		scene.world().restoreBlocks(structure);
		prepareMechanics(scene, util);
		setPortAddress(scene, portPos, STATION_NAME);
		scene.world().showSection(structure, Direction.DOWN);
		scene.idle(30);

		setPortOpen(scene, portPos, true);
		Vec3 landingStart = util.vector().of(0.2, 5.2, 2.5);
		Vec3 landingEnd = util.vector().of(4.1, 3.15, 2.5);
		ElementLink<EntityElement> landingCourier = createCourier(scene, incomingPackage, landingStart,
			landingEnd.subtract(landingStart).scale(1.0 / 26.0),
			AirCourierEntity.Phase.LANDING, AirCourierEntity.Mission.PACKAGE_TO_AIRPORT);
		scene.idle(26);
		scene.world().modifyEntity(landingCourier, Entity::discard);
		scene.effects().indicateSuccess(portPos);
		scene.idle(12);
		setPortOpen(scene, portPos, false);
		scene.idle(52);

		scene.overlay().showOutlineWithText(util.select().position(portPos), 100)
			.attachKeyFrame()
			.colored(PonderPalette.OUTPUT)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(portPos, Direction.NORTH))
			.text("A Phantom Port automatically pushes packages whose address matches its name into adjacent packagers.");
		scene.idle(110);

		PonderHilo.packagerUnpack(scene, packagerPos, incomingPackage);
		scene.idle(30);
		scene.effects().indicateSuccess(chestPos);
		scene.idle(70);
		scene.markAsFinished();
	}

	public static void addressSuffixMatching(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		BlockPos postboxPos = util.grid().at(2, 2, 2);
		BlockPos portPos = util.grid().at(4, 2, 4);
		BlockPos sideFunnelPos = util.grid().at(4, 2, 3);
		BlockPos sideBeltStartPos = util.grid().at(4, 1, 1);
		BlockPos frontFunnelPos = util.grid().at(3, 2, 4);
		BlockPos frontBeltEntryPos = frontFunnelPos.below();
		BlockPos frontBeltEndPos = util.grid().at(1, 1, 4);
		Selection postboxAndPort = util.select().position(postboxPos)
			.add(util.select().position(portPos));
		Selection postboxAndBeam = util.select().fromTo(2, 1, 2, 2, 2, 2);
		Selection sideBelt = util.select().fromTo(4, 1, 1, 4, 1, 3);
		Selection sideCogs = util.select().position(5, 0, 3)
			.add(util.select().position(5, 1, 3));
		Selection sideKinetics = sideBelt.copy()
			.add(sideCogs);
		Selection sideTransferMechanics = sideKinetics.copy()
			.add(util.select().position(sideFunnelPos));
		Selection initialVisible = util.select().everywhere()
			.substract(sideTransferMechanics);
		ItemStack routedPackage = addressedPackage("工厂//铁锭", new ItemStack(Items.IRON_INGOT));
		Vec3 beltLaunchPosition = util.vector().of(1.5, 1.875, 4.5);

		scene.title("phantom_port_address_matching", "Phantom Port Address Matching");
		scene.configureBasePlate(0, 0, 6);
		scene.scaleSceneView(0.9f);
		prepareMechanics(scene, util);

		scene.world().showSection(initialVisible, Direction.DOWN);
		scene.idle(25);

		scene.overlay().showOutlineWithText(postboxAndPort, 90)
			.attachKeyFrame()
			.colored(PonderPalette.BLUE)
			.placeNearTarget()
			.pointAt(util.vector().of(3.5, 2.7, 3.5))
			.text("Phantom Ports use address matching logic similar to Postboxes.");
		scene.idle(100);

		scene.world().hideSection(postboxAndBeam, Direction.DOWN);
		scene.idle(25);

		scene.overlay().showOutlineWithText(util.select().position(portPos), 85)
			.attachKeyFrame()
			.colored(PonderPalette.OUTPUT)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(portPos, Direction.NORTH))
			.text("In addition, Phantom Ports have an extra address matching rule.");
		scene.idle(95);

		scene.world().showSection(sideTransferMechanics, Direction.SOUTH);
		scene.idle(30);

		ElementLink<BeltItemElement> incomingPackage = scene.world().createItemOnBelt(sideBeltStartPos,
			Direction.UP, routedPackage.copy());
		scene.world().stallBeltItem(incomingPackage, true);
		scene.overlay().showText(60)
			.colored(PonderPalette.OUTPUT)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(sideBeltStartPos, Direction.UP))
			.text("-> Factory//Iron Ingot");
		scene.idle(70);

		scene.world().stallBeltItem(incomingPackage, false);
		scene.idle(60);
		scene.world().removeItemsFromBelt(sideBeltStartPos);
		scene.world().removeItemsFromBelt(sideBeltStartPos.south());
		scene.world().removeItemsFromBelt(sideFunnelPos.below());
		scene.world().flapFunnel(sideFunnelPos, false);
		scene.idle(15);

		scene.overlay().showOutlineWithText(util.select().position(portPos), 90)
			.attachKeyFrame()
			.colored(PonderPalette.INPUT)
			.placeNearTarget()
			.pointAt(util.vector().topOf(portPos))
			.text("Phantom Ports ignore any content after // when matching addresses.");
		scene.idle(120);

		ElementLink<BeltItemElement> departingCourier = scene.world().createItemOnBelt(frontBeltEntryPos,
			Direction.UP, MiniPhantomItem.createLoadedWithHeading(routedPackage.copy(),
				AirCourierHelper.getHeadingAngle(Direction.WEST)));
		orientBeltCourierForTravel(scene, departingCourier, Direction.WEST);
		scene.world().stallBeltItem(departingCourier, true);
		scene.overlay().showText(85)
			.colored(PonderPalette.OUTPUT)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(frontFunnelPos, Direction.DOWN))
			.text("This package will be sent to the Phantom Port at address Factory.");
		scene.idle(95);

		moveCourierToBeltEnd(scene, frontBeltEntryPos, frontBeltEndPos, departingCourier);
		ElementLink<EntityElement> outboundCourier = createCourier(scene, routedPackage,
			beltLaunchPosition, new Vec3(-0.15, 0.12, 0),
			AirCourierEntity.Phase.TAKEOFF, AirCourierEntity.Mission.PACKAGE_TO_AIRPORT);
		scene.idle(35);
		scene.world().modifyEntity(outboundCourier, Entity::discard);
		scene.idle(10);
		scene.markAsFinished();
	}

	public static void manualFireworkLaunch(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		Selection floor = util.select().fromTo(0, 0, 0, 4, 0, 4);
		Vec3 groundedPosition = util.vector().of(2.5, 1.01, 2.5);
		Vec3 launchDirection = new Vec3(-1, 0, 0);
		ItemStack cargoPackage = addressedPackage(PLAYER_NAME);

		scene.title("phantom_port_manual_launch", "Launching Cargo Phantoms with Firework Rockets");
		scene.configureBasePlate(0, 0, 5);

		scene.world().showSection(floor, Direction.UP);
		scene.idle(20);

		ElementLink<EntityElement> groundedCourier = scene.world().createEntity(world -> {
			AirCourierEntity courier = AirCourierEntity.createWaiting(world, cargoPackage.copy(), launchDirection);
			courier.setPos(groundedPosition);
			courier.setMission(AirCourierEntity.Mission.PACKAGE_TO_PLAYER);
			return courier;
		});
		scene.idle(20);

		scene.overlay().showText(80)
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(groundedPosition.add(0, 0.45, 0))
			.text("A Firework Rocket can be consumed to launch a grounded Cargo Phantom.");
		scene.idle(90);

		scene.overlay().showControls(groundedPosition.add(0, 0.75, 0), Pointing.DOWN, 50)
			.rightClick()
			.withItem(new ItemStack(Items.FIREWORK_ROCKET));
		scene.idle(60);

		scene.world().modifyEntity(groundedCourier, entity -> {
			if (entity instanceof AirCourierEntity courier) {
				courier.setPhase(AirCourierEntity.Phase.TAKEOFF);
				courier.setDeltaMovement(launchDirection.scale(0.28).add(0, 0.15, 0));
			}
		});
		scene.idle(35);
		scene.world().modifyEntity(groundedCourier, Entity::discard);
		scene.idle(10);
		scene.markAsFinished();
	}

	public static void playerDelivery(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		BlockPos portPos = util.grid().at(4, 2, 2);
		BlockPos funnelPos = util.grid().at(3, 2, 2);
		BlockPos beltEntryPos = funnelPos.below();
		BlockPos beltEndPos = util.grid().at(1, 1, 2);
		Selection playerFloor = util.select().fromTo(0, 0, 0, 4, 0, 4);
		ItemStack addressedPackage = addressedPackage(PLAYER_NAME);
		Vec3 beltLaunchPosition = util.vector().of(1.5, 1.875, 2.5);

		scene.title("phantom_port_player_delivery", "Delivering Packages to Players");
		scene.configureBasePlate(0, 0, 5);
		prepareMechanics(scene, util);

		scene.world().showSection(util.select().layer(0), Direction.UP);
		scene.idle(8);
		scene.world().showSection(util.select().fromTo(1, 1, 2, 5, 1, 3), Direction.NORTH);
		scene.idle(8);
		scene.world().showSection(util.select().position(portPos), Direction.DOWN);
		scene.idle(15);

		scene.overlay().showOutline(PonderPalette.INPUT, "phantom_port_funnel", util.select().position(portPos), 80);
		scene.overlay().showText(80)
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(portPos, Direction.WEST))
			.text("Place the Phantom Port on a belt and attach an Andesite Funnel to its front.");
		scene.idle(90);
		scene.world().showSection(util.select().position(funnelPos), Direction.EAST);
		scene.effects().indicateSuccess(funnelPos);
		scene.idle(20);

		scene.overlay().showControls(util.vector().topOf(portPos), Pointing.DOWN, 50)
			.withItem(AllItems.MINI_PHANTOM.asStack());
		scene.idle(60);

		scene.overlay().showControls(util.vector().topOf(portPos), Pointing.DOWN, 50)
			.withItem(addressedPackage);
		scene.overlay().showText(55)
			.placeNearTarget()
			.pointAt(util.vector().topOf(portPos))
			.colored(PonderPalette.OUTPUT)
			.text("-> Li Hua");
		scene.idle(60);

		ElementLink<BeltItemElement> stagedCourier =
			scene.world().createItemOnBelt(beltEntryPos, Direction.UP, MiniPhantomItem.createLoaded(addressedPackage));
		scene.world().stallBeltItem(stagedCourier, true);
		scene.overlay().showText(65)
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(funnelPos, Direction.DOWN))
			.text("Once everything is ready, the Cargo Phantom takes off from the belt carrying the package.");
		scene.idle(65);
		moveCourierToBeltEnd(scene, beltEntryPos, beltEndPos, stagedCourier);
		ElementLink<EntityElement> outboundCourier = createCourier(scene, addressedPackage,
			beltLaunchPosition, new Vec3(-0.15, 0.12, 0),
			AirCourierEntity.Phase.TAKEOFF, AirCourierEntity.Mission.PACKAGE_TO_PLAYER);
		scene.world().hideSection(util.select().everywhere(), Direction.UP);
		scene.idle(30);
		scene.world().modifyEntity(outboundCourier, Entity::discard);
		scene.overlay().showText(70)
			.attachKeyFrame()
			.text("Cargo Phantoms can cross unloaded chunks and travel between dimensions.");
		scene.idle(50);

		scene.world().setBlocks(playerFloor, Blocks.NETHERRACK.defaultBlockState(), false);
		scene.world().showSection(playerFloor, Direction.UP);
		scene.idle(20);
		ElementLink<EntityElement> zombieEntity = scene.world().createEntity(world -> {
			Vec3 playerPos = util.vector().topOf(util.grid().at(2, 0, 2));
			Zombie zombie = new Zombie(world);
			zombie.setPos(playerPos);
			zombie.xo = playerPos.x;
			zombie.yo = playerPos.y;
			zombie.zo = playerPos.z;
			zombie.lookAt(EntityAnchorArgument.Anchor.FEET, util.vector().of(3.5, 1.5, 0));
			return zombie;
		});
		scene.overlay().showText(55)
			.placeNearTarget()
			.pointAt(util.vector().of(2.5, 2.7, 2.5))
			.colored(PonderPalette.BLUE)
			.text("Li Hua");
		scene.idle(60);

		Vec3 deliveryStart = util.vector().of(5.8, 5.2, 2.5);
		Vec3 deliveryEnd = util.vector().of(3.6, 2.0, 2.5);
		ElementLink<EntityElement> incomingCourier = createCourier(scene, addressedPackage, deliveryStart,
			deliveryEnd.subtract(deliveryStart).scale(1.0 / 20.0),
			AirCourierEntity.Phase.LANDING, AirCourierEntity.Mission.PACKAGE_TO_PLAYER);
		scene.idle(20);
		scene.world().modifyEntity(incomingCourier, entity -> {
			if (entity instanceof AirCourierEntity courier) {
				courier.setPackage(ItemStack.EMPTY);
				courier.setPhase(AirCourierEntity.Phase.TAKEOFF);
				courier.setMission(AirCourierEntity.Mission.CARRIER_RETURN);
				courier.setDeltaMovement(new Vec3(0.15, 0.12, 0));
			}
		});
		scene.world().modifyEntity(zombieEntity, entity -> {
			if (entity instanceof Zombie zombie) {
				zombie.setItemInHand(InteractionHand.MAIN_HAND, addressedPackage.copy());
			}
		});
		scene.effects().indicateSuccess(util.grid().at(2, 0, 2));
		scene.idle(8);
		scene.overlay().showText(55)
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().of(2.5, 2.3, 2.5))
			.text("After delivering the package to the player, the Cargo Phantom automatically returns.");
		scene.idle(22);
		scene.world().modifyEntity(incomingCourier, Entity::discard);
		scene.idle(50);
		scene.markAsFinished();
	}

	public static void stationDelivery(SceneBuilder builder, SceneBuildingUtil util) {
		CreateSceneBuilder scene = new CreateSceneBuilder(builder);
		BlockPos portPos = util.grid().at(4, 2, 2);
		BlockPos funnelPos = util.grid().at(3, 2, 2);
		BlockPos beltEntryPos = funnelPos.below();
		BlockPos beltEndPos = util.grid().at(1, 1, 2);
		ItemStack addressedPackage = addressedPackage(STATION_NAME);
		Vec3 beltLaunchPosition = util.vector().of(1.5, 1.875, 2.5);

		scene.title("phantom_port_station_delivery", "Delivering Packages to Phantom Ports");
		scene.configureBasePlate(0, 0, 5);
		prepareMechanics(scene, util);
		scene.world().showSection(util.select().everywhere(), Direction.DOWN);
		scene.idle(25);

		scene.overlay().showControls(util.vector().topOf(portPos), Pointing.DOWN, 50)
			.withItem(AllItems.MINI_PHANTOM.asStack());
		scene.idle(60);

		scene.overlay().showControls(util.vector().topOf(portPos), Pointing.DOWN, 50)
			.withItem(addressedPackage);
		scene.overlay().showText(55)
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().topOf(portPos))
			.colored(PonderPalette.OUTPUT)
			.text("-> \"Factory\"");
		scene.idle(60);

		moveCourierToBeltEnd(scene, beltEntryPos, beltEndPos, MiniPhantomItem.createLoaded(addressedPackage));
		ElementLink<EntityElement> outboundCourier = createCourier(scene, addressedPackage,
			beltLaunchPosition, new Vec3(-0.15, 0.12, 0),
			AirCourierEntity.Phase.TAKEOFF, AirCourierEntity.Mission.PACKAGE_TO_AIRPORT);
		scene.idle(30);
		scene.world().modifyEntity(outboundCourier, Entity::discard);
		scene.world().hideSection(util.select().everywhere(), Direction.UP);
		scene.idle(30);

		scene.world().showSection(util.select().everywhere(), Direction.EAST);
		scene.idle(15);

		setPortOpen(scene, portPos, true);
		Vec3 landingStart = util.vector().of(0.2, 5.2, 2.5);
		Vec3 landingEnd = util.vector().of(4.1, 3.15, 2.5);
		ElementLink<EntityElement> landingCourier = createCourier(scene, addressedPackage, landingStart,
			landingEnd.subtract(landingStart).scale(1.0 / 26.0),
			AirCourierEntity.Phase.LANDING, AirCourierEntity.Mission.PACKAGE_TO_AIRPORT);
		scene.overlay().showText(60)
			.attachKeyFrame()
			.placeNearTarget()
			.pointAt(util.vector().topOf(portPos))
			.text("The Cargo Phantom delivers the package to the Phantom Port with the matching name.");
		scene.idle(26);
		scene.world().modifyEntity(landingCourier, Entity::discard);
		scene.effects().indicateSuccess(portPos);
		scene.idle(12);
		setPortOpen(scene, portPos, false);
		scene.idle(45);

		ElementLink<BeltItemElement> returningBeltCourier =
			scene.world().createItemOnBelt(beltEntryPos, Direction.UP, AllItems.MINI_PHANTOM.asStack());
		orientBeltCourierForTravel(scene, returningBeltCourier, Direction.WEST);
		scene.world().stallBeltItem(returningBeltCourier, true);
		scene.overlay().showText(75)
			.placeNearTarget()
			.pointAt(util.vector().blockSurface(funnelPos, Direction.DOWN))
			.text("After completing the delivery, the Cargo Phantom automatically returns.");
		scene.idle(75);
		moveCourierToBeltEnd(scene, beltEntryPos, beltEndPos, returningBeltCourier);
		ElementLink<EntityElement> returningCourier = createCourier(scene, ItemStack.EMPTY, beltLaunchPosition,
			new Vec3(-0.15, 0.12, 0),
			AirCourierEntity.Phase.TAKEOFF, AirCourierEntity.Mission.CARRIER_RETURN);
		scene.idle(30);
		scene.world().modifyEntity(returningCourier, Entity::discard);
		scene.idle(15);
		scene.markAsFinished();
	}

	private static void moveCourierToBeltEnd(CreateSceneBuilder scene, BlockPos beltEntryPos, BlockPos beltEndPos,
		ItemStack courierStack) {
		ElementLink<BeltItemElement> courier =
			scene.world().createItemOnBelt(beltEntryPos, Direction.UP, courierStack);
		moveCourierToBeltEnd(scene, beltEntryPos, beltEndPos, courier);
	}

	private static void moveCourierToBeltEnd(CreateSceneBuilder scene, BlockPos beltEntryPos, BlockPos beltEndPos,
		ElementLink<BeltItemElement> courier) {
		scene.world().stallBeltItem(courier, false);
		scene.idle(65);
		scene.world().removeItemsFromBelt(beltEntryPos);
		scene.world().removeItemsFromBelt(beltEntryPos.west());
		scene.world().removeItemsFromBelt(beltEndPos);
		scene.idle(2);
	}

	private static void orientBeltCourierForTravel(CreateSceneBuilder scene,
		ElementLink<BeltItemElement> courier, Direction movementDirection) {
		scene.addInstruction(ponderScene -> {
			BeltItemElement resolved = ponderScene.resolve(courier);
			if (resolved == null) {
				return;
			}
			resolved.ifPresent(transported -> {
				MiniPhantomItem.setHeadingAngle(transported.stack,
					AirCourierHelper.getHeadingAngle(movementDirection));
				transported.angle = 180;
				transported.sideOffset = transported.prevSideOffset = transported.getTargetSideOffset();
			});
		});
	}

	private static void prepareMechanics(CreateSceneBuilder scene, SceneBuildingUtil util) {
		scene.world().setKineticSpeed(util.select().everywhere(), 16);
	}

	private static ItemStack addressedPackage(String address) {
		ItemStack box = PackageStyles.getDefaultBox();
		PackageItem.addAddress(box, address);
		return box;
	}

	private static ItemStack addressedPackage(String address, ItemStack contents) {
		ItemStack box = PackageItem.containing(List.of(contents.copy()));
		PackageItem.addAddress(box, address);
		return box;
	}

	private static ElementLink<EntityElement> createCourier(CreateSceneBuilder scene, ItemStack box, Vec3 position,
		Vec3 motion, AirCourierEntity.Phase phase, AirCourierEntity.Mission mission) {
		return scene.world().createEntity(world -> createCourier(world, box, position, motion, phase, mission));
	}

	private static AirCourierEntity createCourier(Level world, ItemStack box, Vec3 position, Vec3 motion,
		AirCourierEntity.Phase phase, AirCourierEntity.Mission mission) {
		AirCourierEntity courier = AirCourierEntity.createWaiting(world, box, motion);
		courier.setPos(position);
		courier.setPhase(phase);
		courier.setMission(mission);
		courier.setDeltaMovement(motion);
		return courier;
	}

	private static void setPortOpen(CreateSceneBuilder scene, BlockPos portPos, boolean open) {
		scene.world().modifyBlock(portPos, state -> state.setValue(PhantomPortBlock.OPEN, open), false);
	}

	private static void setPortAddress(CreateSceneBuilder scene, BlockPos portPos, String address) {
		scene.world().modifyBlockEntity(portPos, PhantomPortBlockEntity.class, be -> be.addressFilter = address);
	}
}
