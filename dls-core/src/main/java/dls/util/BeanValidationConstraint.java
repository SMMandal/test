package dls.util;

public final class BeanValidationConstraint {

    private BeanValidationConstraint() {}

    // word characters, timestamp characters, one space between words and at least one character
    public static final String ORGANIZATION_REGEX = "^([\\w-.:][ ]?)*[a-zA-Z]([\\w-.:][ ]?)*$";
    public static final int ORGANIZATION_LEN_MIN = 3;
    public static final int ORGANIZATION_LEN_MAX = 100;

    // word characters, timestamp characters, one space between words and at least one character
    public static final String ORGPOS_REGEX = "^(([\\w-.:][ ]?)*[a-zA-Z]([\\w-.:][ ]?)*)(\\/\\w+)+$";
    public static final int ORGPOS_LEN = 500;

    // user names with word characters, `.` and , emails
    public static final String USERNAME_REGEX = "^([\\w-.]+[@]?[\\w]+([.]?[a-zA-Z]+){3})|([\\w]+)$";
    public static final int USERNAME_LEN = 50;

    public static final String URL_REGEX = "://";

    // depth of directory hierarchy allowed is 10
    public static final String SAVEPOINT_REGEX = "^((/)?([\\w-.:]*[a-zA-Z][\\w-.:]*)+){1,10}$";
    public static final int SAVEPOINT_LEN = 50;

    public static final String FILENAME_REGEX = "^(([^%\\/<>:\"\\\\|?*]+)|(\\w+:\\/\\/.+))$";
    public static final int FILENAME_LEN = 255;


    public static final String DIRECTORY_REGEX = "^[^#%&{}\\\\<>*?$!'\":@+`|=]+$";
//    public static final String DIRECTORY_REGEX = "^[^#%&{}\\<>*?\\s$!'\":@+`|=]+$";
    public static final String ENFORCEMENT_REGEX = "(STRICT|strict|STANDARD|standard)";

    public static final String DIRECTORY_META_REGEX = "(([^\\s,=]+=[^,=]+)(?:,\\s*)?)+";
    public static final int  DIRECTORY_LEN = 255;
    public static final int  DIRECTORY_PATH_LEN = 1024;
    public static final String PERMISSION_ACTION_REGEX = "[Rr]?[Ww]?[Dd]?";



}
