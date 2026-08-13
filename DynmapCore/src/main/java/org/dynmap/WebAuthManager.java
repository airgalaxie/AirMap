package org.dynmap;

public class WebAuthManager {
    public static String esc(String s) {
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if(c == '\\')
                sb.append("\\\\");
            else if(c == '\'')
                sb.append("\\\'");
            else
                sb.append(c);
        }
        return sb.toString();
    }

    static String getDisabledAccessPHP(DynmapCore core, boolean wrap) {
        StringBuilder sb = new StringBuilder();
        if (wrap) {
            sb.append("<?php\n");
        }
        
        core.getDefaultMapStorage().addPaths(sb, core);
        
        if (wrap) {
            sb.append("?>\n");
        }
        
        return sb.toString();
    }
}
