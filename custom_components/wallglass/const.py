"""Constants for the Wallglass integration."""

DOMAIN = "wallglass"
PLATFORMS = ["binary_sensor", "light", "select", "switch"]

PAGES = ["start", "gate", "cameras", "security", "covers", "heating", "rooms", "todo", "settings", "waste", "assistant", "music"]

# Option keys (one per field of the options flow)
OPT_WEATHER = "weather"
OPT_DOORBELL_CAMERA = "doorbell_camera"
OPT_DOORBELL_RING = "doorbell_ring"
OPT_DOORBELL_OPEN = "doorbell_open"
OPT_DOORBELL_MUTE = "doorbell_mute"
OPT_TALK = "talk_go2rtc"
# talk-back settings of an earlier version (direct ISAPI login); dropped from the options
LEGACY_OPTIONS = ("talk_host", "talk_username", "talk_password")
OPT_CAMERAS = "cameras"
OPT_GO2RTC = "go2rtc_rtsp"
OPT_GO2RTC_API = "go2rtc_api"
OPT_ALARMS = "alarms"
OPT_OPENINGS = "openings"
OPT_COVERS = "covers"
OPT_HEATING_SWITCH = "heating_switch"
OPT_HEATING_TIMER = "heating_timer"
OPT_HEATING_ADD_HOUR = "heating_add_hour"
OPT_HEATING_STOP = "heating_stop"
OPT_HEATING_SCHEDULES = "heating_schedules"
OPT_ROOMS = "rooms"
OPT_TODO = "todo"
OPT_MEDIA = "media_players"
OPT_WASTE = "waste"
OPT_CHIPS = "chips"
OPT_WALLPAPER = "wallpaper"
OPT_WALLPAPER_IMAGE = "wallpaper_image"
OPT_SCREENSAVER = "screensaver_seconds"
OPT_NIGHT_FROM = "night_from"
OPT_NIGHT_TO = "night_to"
OPT_GESTURES = ("gesture_up", "gesture_down", "gesture_left", "gesture_right")

SIGNAL_PANEL_UPDATED = "wallglass_updated_{}"
