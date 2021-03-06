#define FM_FAILURE (-1)
#define FM_SUCCESS 0

#define V4L2_CID_USER_S610_BASE (0x00980900 + 0x1070)
enum s610_ctrl_id {
  V4L2_CID_S610_CH_SPACING = (V4L2_CID_USER_S610_BASE + 0x01),
  V4L2_CID_S610_CH_BAND = (V4L2_CID_USER_S610_BASE + 0x02),
  V4L2_CID_S610_SOFT_STEREO_BLEND = (V4L2_CID_USER_S610_BASE + 0x03),
  V4L2_CID_S610_SOFT_STEREO_BLEND_COEFF = (V4L2_CID_USER_S610_BASE + 0x04),
  V4L2_CID_S610_SOFT_MUTE_COEFF = (V4L2_CID_USER_S610_BASE + 0x5),
  V4L2_CID_S610_RSSI_CURR = (V4L2_CID_USER_S610_BASE + 0x06),
  V4L2_CID_S610_SNR_CURR = (V4L2_CID_USER_S610_BASE + 0x07),
  V4L2_CID_S610_SEEK_CANCEL = (V4L2_CID_USER_S610_BASE + 0x08),
  V4L2_CID_S610_SEEK_MODE = (V4L2_CID_USER_S610_BASE + 0x09),
  V4L2_CID_S610_RDS_ON = (V4L2_CID_USER_S610_BASE + 0x0A),
  V4L2_CID_S610_IF_COUNT1 = (V4L2_CID_USER_S610_BASE + 0x0B),
  V4L2_CID_S610_IF_COUNT2 = (V4L2_CID_USER_S610_BASE + 0x0C),
  V4L2_CID_S610_RSSI_TH = (V4L2_CID_USER_S610_BASE + 0x0D),
  V4L2_CID_S610_KERNEL_VER = (V4L2_CID_USER_S610_BASE + 0x0E),
  V4L2_CID_S610_SOFT_STEREO_BLEND_REF = (V4L2_CID_USER_S610_BASE + 0x0F),
  V4L2_CID_S610_REG_RW_ADDR = (V4L2_CID_USER_S610_BASE + 0x10),
  V4L2_CID_S610_REG_RW = (V4L2_CID_USER_S610_BASE + 0x11),
};

/* Tunner modes */
enum fm_tuner_mode {
  FM_TUNER_STOP_SEARCH_MODE = 0,
  FM_TUNER_PRESET_MODE = 1,
  FM_TUNER_AUTONOMOUS_SEARCH_MODE = 2,
  FM_TUNER_AUTONOMOUS_SEARCH_MODE_NEXT = 10
};

/* channel spacing */
enum fm_channel_spacing {
  FM_CHANNEL_SPACING_50KHZ = 1,
  FM_CHANNEL_SPACING_100KHZ = 2,
  FM_CHANNEL_SPACING_200KHZ = 4
};

/* Mute modes */
enum fm_mute_mode { FM_MUTE_ON = 0, FM_MUTE_OFF = 1, FM_MUTE_ATTENUATE = 2 };

/* FM RDS modes */
enum fm_rds_mode { FM_RDS_DISABLE = 0, FM_RDS_ENABLE = 1 };

#define FM_RADIO_RDS_DATA_MAX 48

enum s610_freq_bands {
  S610_BAND_FM = 0,
  S610_BAND_AM = 1,
};

enum s610_aud_mode {
  S610_AUD_ENABLE = 1,
  S610_AUD_DISABLE = 0,
};
