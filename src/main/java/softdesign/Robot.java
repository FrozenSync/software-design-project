package main.java.softdesign;

import main.java.softdesign.map.CartesianCoordinate;
import main.java.softdesign.map.Map;
import simbad.sim.Agent;
import simbad.sim.CameraSensor;
import simbad.sim.RobotFactory;

import javax.vecmath.Point3d;
import javax.vecmath.Vector3d;
import java.awt.image.BufferedImage;

public class Robot extends Agent {

	private static double DIRECTION_CHANGE_PROBABILITY = 0.01;
	private static double BREAKDOWN_PROBABILITY = 0.001;

	public enum Direction {

		NORTH, EAST, SOUTH, WEST;

		/**
		 * Returns the direction after a single clockwise rotation.
		 *
		 * @return the direction after a single clockwise rotation.
		 */
		public Direction rotate() {
			switch (this) {
				case NORTH:
					return EAST;
				case EAST:
					return SOUTH;
				case SOUTH:
					return WEST;
				case WEST:
					return NORTH;
				default:
					throw new IllegalArgumentException("Unrecognized direction");
			}
		}

		/**
		 * Returns the direction after {@code n} clockwise rotations.
		 *
		 * @param n n amount of times to rotate
		 * @return the direction after all clockwise rotations.
		 */
		public Direction rotate(int n) {
			if (n == 0) {
				return this;
			}

			return rotate().rotate(n - 1);
		}
	}

	private boolean broken = false;

	private Direction currentDirection;
	private CentralStation centralStation;
	private CartesianCoordinate coordinate;

	private CameraSensor backCamera;
	private CameraSensor leftCamera;
	private CameraSensor rightCamera;

	Robot(Vector3d position, String name, CentralStation centralStation) {
		super(position, name);

		this.centralStation = centralStation;
		this.currentDirection = Direction.EAST;

		RobotFactory.addBumperBeltSensor(this, 12);
		RobotFactory.addSonarBeltSensor(this, 4);

		this.leftCamera = RobotFactory.addCameraSensor(this);
		this.backCamera = RobotFactory.addCameraSensor(this);
		this.rightCamera = RobotFactory.addCameraSensor(this);

		this.leftCamera.rotateY(Math.PI / 2);
		this.backCamera.rotateY(Math.PI);
		this.rightCamera.rotateY(-Math.PI / 2);
	}

	@Override
	public void performBehavior() {
		if (broken) {
			return;
		}

		updateCoordinate();
		ensureNeighbouringImagesTaken();

		centralStation.sendCurrentPosition(coordinate);
		centralStation.sendCoveredArea(tileAhead(currentDirection, -1));

		if (!coordinate.isOnGrid()) {
			return;
		}

		if (detectedHardwareFault()) {
			broken = true;
		} else if (!isFrontClear() || isRandomTurn()) {
			stop();
			turnRight();
		} else {
			moveInCurrentDirection();
		}
	}

	private void updateCoordinate() {
		Point3d point = new Point3d();
		getCoords(point);
		coordinate = new CartesianCoordinate(point, centralStation.requestMapSize());
	}

	//takes images from the back, left, and right side if not take yet
	private void ensureNeighbouringImagesTaken() {
		takeImageIfNeeded(currentDirection.rotate(1), rightCamera);
		takeImageIfNeeded(currentDirection.rotate(2), backCamera);
		takeImageIfNeeded(currentDirection.rotate(3), leftCamera);
	}

	private void takeImageIfNeeded(Direction direction, CameraSensor camera) {
		CartesianCoordinate coordinateAhead = tileAhead(direction, 1);

		if (centralStation.requestTile(coordinateAhead) == Map.Tile.EMPTY) {
			BufferedImage image = camera.createCompatibleImage();
			centralStation.sendImage(image);
			camera.copyVisionImage(image);

			centralStation.sendCoveredArea(coordinateAhead);
		}
	}

	private boolean detectedHardwareFault() {
		return Math.random() <= BREAKDOWN_PROBABILITY;
	}

	private boolean isFrontClear() {
		CartesianCoordinate coordinateAhead = tileAhead(currentDirection, 1);
		Map.Tile tileAhead = centralStation.requestTile(coordinateAhead);
		return tileAhead.isPassable();
	}

	private boolean isRandomTurn() {
		return Math.random() <= DIRECTION_CHANGE_PROBABILITY;
	}

	private CartesianCoordinate tileAhead(Direction direction, int steps) {
		switch (direction) {
			case NORTH:
				return new CartesianCoordinate(coordinate.getX(), coordinate.getZ() - steps);
			case EAST:
				return new CartesianCoordinate(coordinate.getX() + steps, coordinate.getZ());
			case SOUTH:
				return new CartesianCoordinate(coordinate.getX(), coordinate.getZ() + steps);
			case WEST:
				return new CartesianCoordinate(coordinate.getX() - steps, coordinate.getZ());
			default:
				throw new IllegalArgumentException("Unrecognized direction");
		}
	}

	private void stop() {
		setTranslationalVelocity(0);
	}

	private void turnRight() {
		rotateY(-(Math.PI / 2));
		currentDirection = currentDirection.rotate(1);
	}

	private void moveInCurrentDirection() {
		setTranslationalVelocity(1);
	}
}
