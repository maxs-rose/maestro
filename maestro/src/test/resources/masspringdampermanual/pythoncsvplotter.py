import pandas as pd
import matplotlib.pyplot as plt
df = pd.read_csv("/Users/au443759/source/into-cps-association/maestro/maestro/target/masspringdampermanual/initial/working/outputs.csv")
plt.plot(df['time'],df['msd1.x1'], label="msd1x1")
plt.plot(df['time'], df['msd1.v1'], label="msd1v1")
plt.plot(df['time'], df['msd2.x2'], label="msd2x2")
plt.plot(df['time'], df['msd2.v2'], label="msd2v2")
plt.legend()

plt.show()