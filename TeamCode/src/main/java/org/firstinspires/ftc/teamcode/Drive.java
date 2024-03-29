package org.firstinspires.ftc.teamcode;

import static org.firstinspires.ftc.teamcode.Config.DECIMAL_FORMAT;
import static org.firstinspires.ftc.teamcode.Config.FINE_TUNE_MODIFIER;
import static org.firstinspires.ftc.teamcode.Config.LOGO_DIRECTION;
import static org.firstinspires.ftc.teamcode.Config.STRAFING_MODIFIER;
import static org.firstinspires.ftc.teamcode.Config.USB_DIRECTION;

import com.qualcomm.hardware.rev.RevHubOrientationOnRobot;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.IMU;

import org.firstinspires.ftc.robotcore.external.Telemetry;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;

/**
 * A wrapper around a Mecanum drive train, allowing for robot- or field-centric driving and gamepad or programmatic input.
 */
public class Drive {
	private final IMU imu;

	private final DcMotorEx frontLeft;
	private final DcMotorEx backLeft;
	private final DcMotorEx frontRight;
	private final DcMotorEx backRight;

	private final Telemetry.Item headingItem;

	private final boolean useHeading;
	private final boolean isAuton;

	private final Telemetry.Item debugHeadingItem;
	private final Telemetry.Item debugGamepadItem;

	/**
	 * Flags to modify the behavior of the driving code.
	 */
	public enum Flag {
		/**
		 * Disable the usage of heading, making the system robot-centric.
		 */
		DISABLE_HEADING,
		/**
		 * Enable autonomous control, disabling all gamepad input.
		 */
		AUTON,
	}

	/**
	 * Create a field-centric drive system.
	 *
	 * @param hardwareMap Hardware map used to initialize motors and the IMU.
	 * @param telemetry Telemetry object for logging.
	 */
	public Drive(HardwareMap hardwareMap, Telemetry telemetry) {
		this(hardwareMap, telemetry, true, false);
	}

	/**
	 * Create a drive system with a modifier detailed in Flag.
	 *
	 * @param hardwareMap Hardware map used to initialize motors and the IMU.
	 * @param telemetry Telemetry object for logging.
	 * @param flag Robot behavior modifier.
	 * @see Flag
	 */
	public Drive(HardwareMap hardwareMap, Telemetry telemetry, Flag flag) {
		this(hardwareMap, telemetry, flag != Flag.DISABLE_HEADING, flag == Flag.AUTON);
	}

	private Drive(HardwareMap hardwareMap, Telemetry telemetry, boolean useHeading, boolean isAuton) {
		telemetry.log().add("Setting up drive...");

		this.useHeading = useHeading;
		this.isAuton = isAuton;

		telemetry.log().add("Creating telemetry entries...");

		if (isAuton) {
			headingItem = telemetry.addData("Heading", "Controlled by auton.");
		} else if (useHeading) {
			headingItem = telemetry.addData("Heading", "Press [Y] to reset.");
		} else {
			headingItem = telemetry.addData("Heading", "Heading is disabled.");
		}

		debugHeadingItem = telemetry.addData("[DEBUG] Heading", "");
		debugGamepadItem = telemetry.addData("[DEBUG] Gamepad", "");

		telemetry.log().add("Setting up driving hardware...");
		frontLeft = hardwareMap.get(DcMotorEx.class, "frontLeft");
		backLeft = hardwareMap.get(DcMotorEx.class, "backLeft");
		frontRight = hardwareMap.get(DcMotorEx.class, "frontRight");
		backRight = hardwareMap.get(DcMotorEx.class, "backRight");
		imu = hardwareMap.get(IMU.class, "imu");

		configure(telemetry);

		telemetry.log().add("Driving is ready.");
	}

	/**
	 * Drive using a gamepad. This method should be called constantly in a loop.
	 *
	 * @param gamepad Gamepad to use.
	 * @see GamepadEx
	 */
	public void drive(GamepadEx gamepad) {
		if (isAuton) return;

		if (useHeading) {
			if (gamepad.isPressed(GamepadEx.Button.Y)) {
				imu.resetYaw();
				headingItem.setValue("Heading is reset.");
			} else {
				headingItem.setValue("Press [Y] to reset.");
			}
		}

		double x = (gamepad.isPressed(GamepadEx.Button.RB) ? 1 : 0) - (gamepad.isPressed(GamepadEx.Button.LB) ? 1 : 0);
		double y = -gamepad.getLeftY();
		double turn = gamepad.getLeftX();
		boolean fineTune = gamepad.isPressed(GamepadEx.Button.B);
		debugGamepadItem.setValue(DECIMAL_FORMAT + " | " + DECIMAL_FORMAT + " | " + DECIMAL_FORMAT + " | " + fineTune, x, y, turn);
		drive(x, y, turn, fineTune);
	}

	/**
	 * Drive using a power and an angle.
	 *
	 * @param power Power to drive at, within range [-1, 1].
	 * @param angle Angle to drive at, in radians, within range [-π, π]. 0 radians is forward. Positive values are CW, and negative values are CCW.
	 * @param turn Turning value, within range [-1, 1]. Positive values are CW, and negative values are CCW.
	 */
	public void driveAtAngle(double power, double angle, double turn, boolean fineTune) {
		power = Math.min(1, Math.max(-1, power));
		angle = Math.min(Math.PI, Math.max(-Math.PI, angle));
		turn = Math.min(1, Math.max(-1, turn));

		double x = power * Math.sin(angle);
		double y = power * Math.cos(angle);
		drive(x, y, turn, fineTune);
	}

	/**
	 * Stop all motors in the drive train.
	 */
	public void stop() {
		frontLeft.setPower(0);
		backLeft.setPower(0);
		frontRight.setPower(0);
		backRight.setPower(0);
	}

	private void drive(double x, double y, double turn, boolean fineTune) {
		// https://gm0.org/en/latest/docs/software/tutorials/mecanum-drive.html#robot-centric-final-sample-code
		x *= STRAFING_MODIFIER; // Make strafing more powerful

		if (fineTune) {
			x *= FINE_TUNE_MODIFIER;
			y *= FINE_TUNE_MODIFIER;
			turn *= FINE_TUNE_MODIFIER;
		}

		// https://gm0.org/en/latest/docs/software/tutorials/mecanum-drive.html#field-centric
		YawPitchRollAngles angles = imu.getRobotYawPitchRollAngles();
		double headingDegrees = -angles.getYaw(AngleUnit.DEGREES);
		double heading = -angles.getYaw(AngleUnit.RADIANS);
		debugHeadingItem.setValue(DECIMAL_FORMAT + " deg | " + DECIMAL_FORMAT + " rad", headingDegrees, heading);
		/*
		if (useHeading) {
			x = x * Math.cos(heading) - y * Math.sin(heading);
			y = x * Math.sin(heading) + y * Math.cos(heading);
		}
		*/

		// Clip power range to [-1.0, 1.0]
		double denominator = Math.max(Math.abs(x) + Math.abs(y) + Math.abs(turn), 1);
		frontLeft.setPower((y + x + turn) / denominator);
		backLeft.setPower((y - x + turn) / denominator);
		frontRight.setPower((y - x - turn) / denominator);
		backRight.setPower((y + x - turn) / denominator);
	}

	private void configure(Telemetry telemetry) {
		telemetry.log().add("Configuring motors...");
		frontLeft.setDirection(DcMotorSimple.Direction.FORWARD);
		backLeft.setDirection(DcMotorSimple.Direction.FORWARD);
		frontRight.setDirection(DcMotorSimple.Direction.REVERSE);
		backRight.setDirection(DcMotorSimple.Direction.REVERSE);

		frontLeft.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
		backLeft.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
		frontRight.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
		backRight.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);

		frontLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
		backLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
		frontRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
		backRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

		frontLeft.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
		backLeft.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
		frontRight.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
		backRight.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);

		telemetry.log().add("Initializing IMU...");
		imu.initialize(new IMU.Parameters(new RevHubOrientationOnRobot(LOGO_DIRECTION, USB_DIRECTION)));
	}
}
