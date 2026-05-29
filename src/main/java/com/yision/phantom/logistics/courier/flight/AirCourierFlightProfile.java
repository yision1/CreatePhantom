package com.yision.phantom.logistics.courier.flight;

public record AirCourierFlightProfile(

	int takeoffTicks,
	double takeoffSpeed,
	double takeoffForwardDistance,
	double takeoffAltitudeGain,
	double takeoffSwitchDistance,

	double cruiseSpeed,
	double cruiseTurnDegrees,
	double cruiseCurveNear,
	double cruiseCurveFar,
	double cruiseAltitudeLeadStart,
	double cruiseAltitudeLeadDivisor,
	double cruiseAltitudeLeadCap,
	int cruiseStraightenTicks,
	double cruiseSwitchDistance,

	double landingSpeed,
	double landingTurnDegrees,
	double landingCurveNear,
	double landingCurveFar,

	double curveDistanceMin,
	double curveDistanceMax,

	double phantomPortCruiseHeight,
	double phantomPortLandingHeight,
	double phantomPortCompletionDistance,

	double playerTargetHeight,
	double playerForwardOffset,
	double playerCruiseHeight,
	double playerCompletionDistance,

	double approachGateNearDistance,
	double approachGateHorizontalThreshold,
	double phantomPortApproachHeight,
	double playerApproachHeight,
	int playerApproachGateUpdateTicks,
	double playerApproachGateLerp,
	double playerLandingTargetLerp,

	double landingMinSpeed,
	double landingDecelerationRange,
	double landingMaxDownSpeed,
	double landingMaxUpSpeed
) {
	public static final AirCourierFlightProfile DEFAULT = new AirCourierFlightProfile(

		24, 0.28, 4.5, 4.5, 0.75,

		0.40, 6.0, 0.40, 0.06, 4.0, 3.0, 10.0, 40, 3.0,

		0.32, 12.0, 0.55, 0.18,

		5.0, 60.0,

		4.0, 0.55, 0.2,

		1.2, 0.15, 1.8, 1.5,

		2.0, 5.0, 3.5, 1.6, 5, 0.35, 0.35,

		0.16, 8.0, 0.22, 0.14
	);
}
