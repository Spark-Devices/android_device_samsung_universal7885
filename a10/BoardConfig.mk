DEVICE_PATH := device/samsung/a10

# Asserts
TARGET_OTA_ASSERT_DEVICE := a10,a10dd

# Kernel
TARGET_KERNEL_CONFIG := exynos7885-a10_enforcing_defconfig

# Display
TARGET_SCREEN_DENSITY := 320

# Partitions
BOARD_RECOVERYIMAGE_PARTITION_SIZE := 55574528 # 55MB
BOARD_SYSTEMIMAGE_PARTITION_SIZE := 4320133120 #4.02GB

# Inherit common board flags
include device/samsung/universal7885-common/BoardConfigCommon.mk
