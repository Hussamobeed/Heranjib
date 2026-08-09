import re

def scale_path(path_str, scale, tx, ty):
    def replacer(match):
        num = float(match.group(0))
        return f"{num * scale + tx if match.start() % 2 == 0 else num * scale + ty:.1f}"
    
    parts = re.split(r'([A-Za-z])', path_str)
    res = ""
    for p in parts:
        if re.match(r'[A-Za-z]', p):
            res += p
        else:
            nums = re.findall(r'-?\d+\.?\d*', p)
            if not nums:
                res += p
                continue
            
            # This is a naive scaler that might not work perfectly for complex paths 
            # with relative commands or arcs, but for simple absolute paths it's fine.
            # Actually let's just use a <group> tag with scale and translation in the vector drawable!
            pass
    return path_str

print("Done")
